package com.dublikunt.dmclient.download

import android.content.Context
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import com.dublikunt.dmclient.scrapper.ImageType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadedGalleryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadedGalleryDao
) {
    private val filesRoot: File get() = context.filesDir

    fun galleryDir(galleryId: Int): File =
        GalleryContentLocator.galleryDir(filesRoot, galleryId)

    fun pageFile(galleryId: Int, page: Int, images: List<ImageType>): File =
        GalleryContentLocator.pageFile(filesRoot, galleryId, page, images)

    fun coverFile(galleryId: Int, thumbUrl: String): File =
        GalleryContentLocator.coverFile(filesRoot, galleryId, thumbUrl)

    fun resolveCover(storedRelativePath: String): String =
        GalleryContentLocator.resolveCover(filesRoot, storedRelativePath)

    fun hasFiles(galleryId: Int): Boolean = galleryDir(galleryId).exists()

    suspend fun insert(gallery: DownloadedGallery) = dao.insert(gallery)

    suspend fun getById(galleryId: Int): DownloadedGallery? = dao.getById(galleryId)

    suspend fun delete(galleryId: Int) {
        galleryDir(galleryId).deleteRecursively()
        dao.getById(galleryId)?.let { dao.delete(it) }
    }

    suspend fun deleteAll() {
        File(filesRoot, GalleryContentLocator.ROOT_DIR).deleteRecursively()
        dao.deleteAll()
    }
}
