package com.dublikunt.dmclient.data.repository

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.entity.DownloadEntity
import com.dublikunt.dmclient.data.download.DownloadFiles
import com.dublikunt.dmclient.data.download.GalleryContentLocator
import com.dublikunt.dmclient.data.work.ArchiveWorker
import com.dublikunt.dmclient.data.work.DownloadWorker
import com.dublikunt.dmclient.di.ApplicationScope
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.network.GallerySummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject internal constructor(
    private val db: AppDatabase,
    private val files: DownloadFiles,
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val work = WorkManager.getInstance(context)
    private val archiveEnqueueLock = Mutex()

    /** Observes persisted download states; coverFile points into internal storage. */
    val downloads: Flow<List<DownloadItem>> =
        db.downloads().observeAll().map { rows -> rows.map(::item) }

    init {
        scope.launch(Dispatchers.IO) {
            work.cancelAllWorkByTag("dmclient_download").result.get()
            work.cancelAllWorkByTag("dmclient_archive").result.get()
            File(files.root, "work_payloads").deleteRecursively()
            File(files.root, "galleries").listFiles()?.filter { it.isDirectory }
                ?.forEach { directory ->
                    val id = directory.name.toIntOrNull() ?: return@forEach
                    files.withGallery(id) {
                        if (db.downloads().get(id) == null) directory.deleteRecursively()
                    }
                }
            db.downloads().all()
                .filter { it.state == DownloadState.Queued || it.state == DownloadState.Downloading }
                .forEach { row ->
                    files.withGallery(row.galleryId) {
                        if (db.downloads()
                                .get(row.galleryId) != null && work.getWorkInfosForUniqueWork(
                                downloadWorkName(row.galleryId)
                            ).get().none { !it.state.isFinished }
                        ) {
                            schedule(row.galleryId, ExistingWorkPolicy.KEEP)
                        }
                    }
                }
        }
    }

    /** Observes one gallery's download, or null after deletion. */
    fun observe(id: Int): Flow<DownloadItem?> = db.downloads().observe(id).map { it?.let(::item) }

    /** Persists a queued gallery then enqueues unique connected-network work; completed rows are retained. */
    suspend fun enqueue(detail: GalleryDetail) = withContext(Dispatchers.IO) {
        require(detail.pageCount > 0 && detail.pageTypes.size == detail.pageCount)
        files.withGallery(detail.id) {
            val existing = db.downloads().get(detail.id)
            if (existing?.state == DownloadState.Completed) return@withGallery
            db.withTransaction {
                db.galleries().upsert(detail.entity())
                if (existing == null) db.downloads().upsert(
                    DownloadEntity(
                        detail.id,
                        Json.encodeToString(detail),
                        GalleryContentLocator.relativeCoverPath(detail.id, detail.coverUrl),
                        DownloadState.Queued,
                        0,
                        null,
                        System.currentTimeMillis(),
                        null,
                    )
                ) else db.downloads().queue(detail.id)
            }
            schedule(
                detail.id,
                if (existing == null) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
            )
        }
    }

    /** Retries a persisted download, resuming pages that already exist on disk. */
    suspend fun retry(id: Int) = withContext(Dispatchers.IO) {
        files.withGallery(id) {
            val row = db.downloads().get(id) ?: return@withGallery
            if (row.state == DownloadState.Completed) return@withGallery
            db.downloads().queue(id)
            schedule(id, ExistingWorkPolicy.REPLACE)
        }
    }

    /** Cancels download/archive work, removes its persisted row, and deletes its files. */
    suspend fun cancel(id: Int) = withContext(Dispatchers.IO) {
        work.cancelUniqueWork(downloadWorkName(id)).result.get()
        work.cancelUniqueWork(archiveWorkName(id)).result.get()
        files.withGallery(id) {
            db.downloads().delete(id)
            check(
                !GalleryContentLocator.galleryDir(files.root, id)
                    .exists() || GalleryContentLocator.galleryDir(files.root, id)
                    .deleteRecursively()
            ) { "Cannot delete gallery files" }
        }
    }

    /** Deletes a download, including active work and partial files. */
    suspend fun delete(id: Int) = cancel(id)

    /** Deletes all persisted downloads and their files. */
    suspend fun deleteAll() {
        db.downloads().all().forEach { delete(it.galleryId) }
    }

    /** Starts an archive on collection and emits its state through completion or failure. */
    fun exportArchive(id: Int): Flow<ArchiveState> = flow {
        require(isDownloaded(id)) { "Gallery is not downloaded" }
        val request =
            OneTimeWorkRequestBuilder<ArchiveWorker>().setInputData(workDataOf(ArchiveWorker.KEY_ID to id))
                .build()
        val workId = withContext(Dispatchers.IO) {
            archiveEnqueueLock.withLock {
                val existing = work.getWorkInfosForUniqueWork(archiveWorkName(id)).get()
                    .firstOrNull { !it.state.isFinished }
                if (existing != null) existing.id else {
                    work.enqueueUniqueWork(
                        archiveWorkName(id),
                        ExistingWorkPolicy.KEEP,
                        request
                    ).result.get()
                    request.id
                }
            }
        }
        emitAll(work.getWorkInfoByIdFlow(workId).map { info ->
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> ArchiveState.Completed(
                    info.outputData.getString(
                        ArchiveWorker.KEY_URI
                    ).orEmpty()
                )

                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> ArchiveState.Failed(
                    info.outputData.getString(
                        ArchiveWorker.KEY_ERROR
                    ) ?: "Archive cancelled or failed"
                )

                WorkInfo.State.RUNNING -> ArchiveState.Running
                else -> ArchiveState.Queued
            }
        }.takeThrough { it !is ArchiveState.Completed && it !is ArchiveState.Failed })
    }

    /** Returns true only for a completed persisted download. */
    suspend fun isDownloaded(id: Int): Boolean =
        db.downloads().get(id)?.state == DownloadState.Completed

    private fun schedule(id: Int, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        work.enqueueUniqueWork(downloadWorkName(id), policy, request).result.get()
    }

    private fun item(row: DownloadEntity): DownloadItem {
        val detail = Json.decodeFromString<GalleryDetail>(row.detailJson)
        val cover = File(files.root, row.coverPath)
        return DownloadItem(
            GallerySummary(
                detail.id,
                detail.title,
                if (cover.exists()) cover.absolutePath else detail.coverUrl
            ),
            cover, row.state, row.downloadedPages, detail.pageCount, row.error, row.createdAt
        )
    }
}

internal fun downloadWorkName(id: Int): String = "download_$id"
internal fun archiveWorkName(id: Int): String = "archive_$id"

private fun <T> Flow<T>.takeThrough(predicate: (T) -> Boolean): Flow<T> = flow {
    this@takeThrough.takeWhile { value -> emit(value); predicate(value) }.collect {}
}
