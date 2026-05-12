package com.dublikunt.dmclient.repository

import android.content.Context
import androidx.paging.PagingSource
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadedDao: DownloadedGalleryDao,
    @param:ApplicationContext private val context: Context
) {
    fun getAllPagingSource(): PagingSource<Int, DownloadedGallery> =
        downloadedDao.getAllPagingSource()

    suspend fun getById(id: Int): DownloadedGallery? = downloadedDao.getById(id)

    suspend fun delete(gallery: DownloadedGallery) = downloadedDao.delete(gallery)

    suspend fun deleteAll() = downloadedDao.deleteAll()

    suspend fun insert(gallery: DownloadedGallery) = downloadedDao.insert(gallery)

    fun getGalleryDir(id: Int): File = File(context.filesDir, "galleries/$id")

    fun getAll(): kotlinx.coroutines.flow.Flow<List<DownloadedGallery>> = downloadedDao.getAll()
}
