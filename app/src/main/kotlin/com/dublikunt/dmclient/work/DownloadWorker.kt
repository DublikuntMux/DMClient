package com.dublikunt.dmclient.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dublikunt.dmclient.R
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.scrapper.ImageType
import com.dublikunt.dmclient.scrapper.NHentaiApi
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val galleryJson = inputData.getString(KEY_GALLERY_JSON) ?: return Result.failure()
        val gallery = try {
            Json.decodeFromString<com.dublikunt.dmclient.scrapper.GalleryFullInfo>(galleryJson)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }

        val notificationId = gallery.id
        setForeground(createForegroundInfo(notificationId, gallery.name, 0, gallery.pages))

        val context = applicationContext
        val galleryDir = File(context.filesDir, "galleries/${gallery.id}")
        if (!galleryDir.exists()) galleryDir.mkdirs()

        val db = AppDatabase.getDatabase(context)

        try {
            val coverFile = File(
                galleryDir,
                "cover.${gallery.thumb.split(".").last()}"
            )
            if (!coverFile.exists()) {
                NHentaiApi.downloadImage(gallery.thumb)?.use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            var lastUpdateTime = 0L
            for (i in 1..gallery.pages) {
                if (isStopped) break

                val imageType = gallery.images[i - 1]
                val ext = when (imageType) {
                    ImageType.Jpg -> "jpg"
                    ImageType.Webp -> "webp"
                }
                val baseUrl = when (imageType) {
                    ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery.pagesId}"
                    ImageType.Webp -> "https://i1.nhentai.net/galleries/${gallery.pagesId}"
                }

                val pageFile = File(galleryDir, "$i.$ext")
                if (!pageFile.exists()) {
                    val pageUrl = "$baseUrl/$i.$ext"
                    NHentaiApi.downloadImage(pageUrl)?.use { input ->
                        FileOutputStream(pageFile).use { output ->
                            input.copyTo(output)
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
                tags = gallery.tags,
                artists = gallery.artists,
                characters = gallery.characters
            )
            db.downloadedGalleryDao().insert(downloadedGallery)

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
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading $title")
            .setContentText("$progress/$max pages")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .setProgress(max, progress, false)
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
        const val KEY_GALLERY_JSON = "gallery_json"
        const val KEY_PROGRESS = "progress"
    }
}
