package com.dublikunt.dmclient.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

data class HistoryRow(@Embedded val gallery: GalleryEntity, val lastPage: Int, val openedAt: Long)

@Dao
interface HistoryDao {
    @Query("SELECT galleries.*, history.lastPage, history.openedAt FROM history JOIN galleries ON galleries.id = history.galleryId WHERE instr(lower(title), lower(:query)) > 0 ORDER BY openedAt DESC")
    fun paging(query: String): PagingSource<Int, HistoryRow>

    @Query("SELECT lastPage FROM history WHERE galleryId = :id")
    fun lastPage(id: Int): Flow<Int?>

    @Query("SELECT * FROM history WHERE galleryId = :id")
    suspend fun get(id: Int): HistoryEntity?

    @Query("SELECT * FROM history")
    suspend fun all(): List<HistoryEntity>

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE galleryId = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}
