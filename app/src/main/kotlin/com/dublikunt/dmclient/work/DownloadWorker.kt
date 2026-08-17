package com.dublikunt.dmclient.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dublikunt.dmclient.R
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.scrapper.ImageType
import com.dublikunt.dmclient.scrapper.NHentaiApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val nHentaiApi: NHentaiApi,
    private val downloadedDao: DownloadedGalleryDao
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val payloadPath = inputData.getString(KEY_GALLERY_PATH) ?: return Result.failure()
        val payloadFile = File(payloadPath)
        val gallery = try {
            if (!payloadFile.exists()) return Result.failure()
            Json.decodeFromString<GalleryFullInfo>(payloadFile.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }

        val notificationId = gallery.id
        setForeground(createForegroundInfo(notificationId, gallery.name, 0, gallery.pages))

        val context = applicationContext
        val galleryDir = File(context.filesDir, "galleries/${gallery.id}")
        if (!galleryDir.exists()) galleryDir.mkdirs()

        try {
            val coverFile = File(
                galleryDir,
                "cover.${gallery.thumb.split(".").last()}"
            )
            if (!coverFile.exists()) {
                nHentaiApi.downloadImage(gallery.thumb)?.use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            var lastUpdateTime = 0L
            for (i in 1..gallery.pages) {
                if (isStopped) break

                val imageType = gallery.images.getOrNull(i - 1) ?: ImageType.Jpg
                val ext = when (imageType) {
                    ImageType.Jpg -> "jpg"
                    ImageType.Webp -> "webp"
                    ImageType.Png -> "png"
                }
                val baseUrl = "https://i1.nhentai.net/galleries/${gallery.pagesId}"

                val pageFile = File(galleryDir, "$i.$ext")
                if (!pageFile.exists()) {
                    val pageUrl = "$baseUrl/$i.$ext"
                    val input = nHentaiApi.downloadImage(pageUrl)
                        ?: throw IOException("Failed to download page $i")
                    input.use { stream ->
                        FileOutputStream(pageFile).use { output ->
                            stream.copyTo(output)
                        }
                    }
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 1000 || i == gallery.pages) {
                    setProgress(workDataOf(KEY_PROGRESS to i))
                    setForeground(
                        createForegroundInfo(
                            notificationId,
                            gallery.name,
                            i,
                            gallery.pages
                        )
                    )
                    lastUpdateTime = currentTime
                }
            }

            if (isStopped) {
                return Result.failure()
            }

            val downloadedGallery = DownloadedGallery(
                id = gallery.id,
                title = gallery.name,
                coverPath = coverFile.absolutePath,
                totalPages = gallery.pages,
                pagesId = gallery.pagesId,
                imageTypes = gallery.images,
                parodies = gallery.parodies,
                tags = gallery.tags,
                artists = gallery.artists,
                characters = gallery.characters
            )
            downloadedDao.insert(downloadedGallery)
            payloadFile.delete()

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        progress: Int,
        max: Int
    ): ForegroundInfo {
        val channelId = "download_channel"
        val channelName = "Downloads"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading $title")
            .setContentText("$progress/$max pages")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_GALLERY_PATH = "gallery_path"
        const val KEY_PROGRESS = "progress"
    }
}
