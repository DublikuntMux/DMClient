package com.dublikunt.dmclient.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.download.DownloadFiles
import com.dublikunt.dmclient.data.download.GalleryContentLocator
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.network.NHentaiApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: NHentaiApi,
    private val db: AppDatabase,
    private val files: DownloadFiles,
    private val notifications: Notifications,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getInt(KEY_ID, -1)
        if (id <= 0) return@withContext Result.failure()
        files.withGallery(id) {
            val row = db.downloads().get(id) ?: return@withGallery Result.failure()
            try {
                val detail = Json.decodeFromString<GalleryDetail>(row.detailJson)
                db.downloads().start(id)
                setForeground(notifications.foreground(id, "downloads", "Downloading ${detail.title}", row.downloadedPages, detail.pageCount))
                val directory = GalleryContentLocator.galleryDir(files.root, id)
                if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create gallery directory")
                download(detail.coverUrl, File(files.root, row.coverPath))
                val pageFiles = (1..detail.pageCount).associateWith { GalleryContentLocator.pageFile(files.root, id, it, detail.pageTypes) }
                var completed = pageFiles.values.count { it.isFile && it.length() > 0 }
                db.downloads().progress(id, completed)
                var lastUpdate = 0L
                val progressLock = Mutex()
                val semaphore = Semaphore(3)
                coroutineScope {
                    pageFiles.filterValues { !it.isFile || it.length() == 0L }.map { (page, file) ->
                        async {
                            semaphore.withPermit { download(GalleryContentLocator.remotePageUrl(detail.mediaId, page, detail.pageTypes), file) }
                            progressLock.withLock {
                                completed++
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate >= 500 || completed == detail.pageCount) {
                                    db.downloads().progress(id, completed)
                                    setForeground(notifications.foreground(id, "downloads", "Downloading ${detail.title}", completed, detail.pageCount))
                                    lastUpdate = now
                                }
                            }
                        }
                    }.awaitAll()
                }
                currentCoroutineContext().ensureActive()
                if (isStopped) throw CancellationException("Download stopped")
                db.downloads().complete(id, detail.pageCount, System.currentTimeMillis())
                Result.success()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                db.downloads().fail(id, error.message ?: "Download failed")
                Result.failure()
            }
        }
    }

    private suspend fun download(url: String, target: File) {
        if (target.isFile && target.length() > 0) return
        currentCoroutineContext().ensureActive()
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            api.openImage(url).use { body ->
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                } }
                if (partial.length() == 0L || (body.contentLength() >= 0 && partial.length() != body.contentLength())) throw IOException("Incomplete image download")
            }
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(target)) throw IOException("Cannot save image")
        } finally {
            partial.delete()
        }
    }

    companion object { const val KEY_ID = "gallery_id" }
}
