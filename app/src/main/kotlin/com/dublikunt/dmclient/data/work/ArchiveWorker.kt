package com.dublikunt.dmclient.data.work

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.download.DownloadFiles
import com.dublikunt.dmclient.data.download.GalleryContentLocator
import com.dublikunt.dmclient.data.repository.DownloadState
import com.dublikunt.dmclient.network.GalleryDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@HiltWorker
class ArchiveWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val files: DownloadFiles,
    private val notifications: Notifications,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getInt(KEY_ID, -1)
        if (id <= 0) return@withContext Result.failure()
        files.withGallery(id) {
            var pendingUri: Uri? = null
            var pendingFile: File? = null
            try {
                val row = db.downloads().get(id)?.takeIf { it.state == DownloadState.Completed }
                    ?: throw IOException("Gallery is not downloaded")
                val detail = Json.decodeFromString<GalleryDetail>(row.detailJson)
                setForeground(
                    notifications.foreground(
                        id + 100_000,
                        "archives",
                        "Archiving ${detail.title}"
                    )
                )
                val name =
                    "$id - ${detail.title.take(160)}.zip".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val uri: Uri
                val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    uri = applicationContext.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )
                        ?: throw IOException("Cannot create archive")
                    pendingUri = uri
                    applicationContext.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Cannot open archive")
                } else {
                    @Suppress("DEPRECATION")
                    val directory =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create Downloads directory")
                    val file = File(directory, name)
                    val temporary = File(directory, "$name.part")
                    pendingFile = temporary
                    uri = Uri.fromFile(file)
                    temporary.outputStream()
                }
                output.use { stream ->
                    ZipOutputStream(stream).use { zip ->
                        val directory = GalleryContentLocator.galleryDir(files.root, id)
                        val content = directory.listFiles()
                            ?.filter { it.isFile && !it.name.endsWith(".part") }
                            ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
                            ?: throw IOException("Gallery files are missing")
                        for (file in content) {
                            currentCoroutineContext().ensureActive()
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    zip.write(buffer, 0, count)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) applicationContext.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
                else pendingFile?.let { temporary ->
                    if (!temporary.renameTo(
                            File(
                                temporary.parentFile,
                                name
                            )
                        )
                    ) throw IOException("Cannot save archive")
                }
                pendingUri = null
                pendingFile = null
                Result.success(workDataOf(KEY_URI to uri.toString()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Archive failed")))
            } finally {
                pendingUri?.let { applicationContext.contentResolver.delete(it, null, null) }
                pendingFile?.delete()
            }
        }
    }

    companion object {
        const val KEY_ID = "gallery_id"
        const val KEY_URI = "archive_uri"
        const val KEY_ERROR = "archive_error"
    }
}
