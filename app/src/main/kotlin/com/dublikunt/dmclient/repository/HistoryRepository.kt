package com.dublikunt.dmclient.repository

import androidx.paging.PagingSource
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: GalleryHistoryDao
) {
    fun getAllPagingSource(): PagingSource<Int, GalleryHistory> = historyDao.getAllPagingSource()

    suspend fun getAllHistory(): List<GalleryHistory> = historyDao.getAllHistory()

    suspend fun deleteHistory(gallery: GalleryHistory) = historyDao.deleteHistory(gallery)

    suspend fun insertHistory(history: GalleryHistory) = historyDao.insertHistory(history)
}
