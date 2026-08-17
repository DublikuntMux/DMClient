package com.dublikunt.dmclient.repository

import androidx.paging.PagingSource
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadedDao: DownloadedGalleryDao
) {
    fun getAllPagingSource(): PagingSource<Int, DownloadedGallery> =
        downloadedDao.getAllPagingSource()

    suspend fun getById(id: Int): DownloadedGallery? = downloadedDao.getById(id)

    suspend fun delete(gallery: DownloadedGallery) = downloadedDao.delete(gallery)

    suspend fun deleteAll() = downloadedDao.deleteAll()

    suspend fun insert(gallery: DownloadedGallery) = downloadedDao.insert(gallery)

    fun getAll(): Flow<List<DownloadedGallery>> = downloadedDao.getAll()
}
