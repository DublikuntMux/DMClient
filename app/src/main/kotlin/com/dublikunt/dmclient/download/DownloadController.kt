package com.dublikunt.dmclient.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.work.ArchiveWorker
import com.dublikunt.dmclient.work.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadPhase {
    data object Idle : DownloadPhase
    data object Running : DownloadPhase
    data object Succeeded : DownloadPhase
    data object Failed : DownloadPhase
}

internal object DownloadPayload {
    private val json = Json

    fun encode(gallery: GalleryFullInfo): String = json.encodeToString(gallery)

    fun decode(text: String): GalleryFullInfo? =
        try {
            json.decodeFromString<GalleryFullInfo>(text)
        } catch (_: Exception) {
            null
        }
}

@Singleton
class DownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DownloadedGalleryStore
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun start(gallery: GalleryFullInfo) {
        withContext(Dispatchers.IO) {
            val payloadFile = payloadFile(gallery.id)
            payloadFile.parentFile?.mkdirs()
            payloadFile.writeText(DownloadPayload.encode(gallery))
        }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(DownloadWorker.KEY_GALLERY_PATH to payloadFile(gallery.id).absolutePath)
            )
            .addTag(DOWNLOAD_TAG)
            .build()
        workManager.enqueueUniqueWork(
            uniqueDownloadName(gallery.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun archive(galleryId: Int, galleryName: String) {
        val request = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setInputData(
                workDataOf(
                    ArchiveWorker.KEY_ID to galleryId,
                    ArchiveWorker.KEY_NAME to galleryName
                )
            )
            .addTag(ARCHIVE_TAG)
            .build()
        workManager.enqueueUniqueWork(
            uniqueArchiveName(galleryId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(galleryId: Int) {
        workManager.cancelUniqueWork(uniqueDownloadName(galleryId))
    }

    fun observe(galleryId: Int): Flow<DownloadPhase> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueDownloadName(galleryId))
            .map { infos -> phaseFrom(infos.firstOrNull()) }

    fun observeArchive(galleryId: Int): Flow<DownloadPhase> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueArchiveName(galleryId))
            .map { infos -> phaseFrom(infos.firstOrNull()) }

    suspend fun delete(galleryId: Int) {
        cancel(galleryId)
        payloadFile(galleryId).delete()
        store.delete(galleryId)
    }

    suspend fun deleteAllDownloads() {
        workManager.cancelAllWorkByTag(DOWNLOAD_TAG)
        workManager.cancelAllWorkByTag(ARCHIVE_TAG)
        payloadDir().deleteRecursively()
        store.deleteAll()
    }

    internal fun payloadFile(galleryId: Int): File =
        File(payloadDir(), "download_$galleryId.json")

    private fun payloadDir(): File = File(context.filesDir, "work_payloads")

    companion object {
        private const val DOWNLOAD_TAG = "dmclient_download"
        private const val ARCHIVE_TAG = "dmclient_archive"

        internal fun uniqueDownloadName(galleryId: Int) = "download_$galleryId"
        internal fun uniqueArchiveName(galleryId: Int) = "archive_$galleryId"

        internal fun phaseFrom(info: WorkInfo?): DownloadPhase = when (info?.state) {
            null -> DownloadPhase.Idle
            WorkInfo.State.SUCCEEDED -> DownloadPhase.Succeeded
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> DownloadPhase.Failed
            else -> DownloadPhase.Running
        }
    }
}
