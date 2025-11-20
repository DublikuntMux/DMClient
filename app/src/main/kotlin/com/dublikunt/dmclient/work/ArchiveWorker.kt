package com.dublikunt.dmclient.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dublikunt.dmclient.R
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val galleryJson = inputData.getString(KEY_GALLERY_JSON) ?: return Result.failure()
        val gallery = try {
            Json.decodeFromString<GalleryFullInfo>(galleryJson)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }

        val notificationId = gallery.id + 100000
        setForeground(createForegroundInfo(notificationId, gallery.name))

        val context = applicationContext
        val galleryDir = File(context.filesDir, "galleries/${gallery.id}")

        if (!galleryDir.exists()) {
            return Result.failure()
        }

        val zipFileName =
            "${gallery.id} - ${gallery.name}.zip".replace(Regex("[\\\\/:*?\"<>|]"), "_")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Result.failure()

                resolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        zipFolder(galleryDir, zipOut)
                    }
                }
            } else {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val zipFile = File(downloadsDir, zipFileName)
                zipFile.outputStream().use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        zipFolder(galleryDir, zipOut)
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private fun zipFolder(folder: File, zipOut: ZipOutputStream) {
        val files = folder.listFiles() ?: return
        files.sortBy {
            it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE
        }

        for (file in files) {
            if (file.isDirectory) continue
            FileInputStream(file).use { fi ->
                BufferedInputStream(fi).use { origin ->
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    origin.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String
    ): ForegroundInfo {
        val channelId = "archive_channel"
        val channelName = "Archiving"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Archiving $title")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
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
    }
}
