package com.dublikunt.dmclient.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dublikunt.dmclient.data.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observeAll(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE galleryId = :id") fun observe(id: Int): Flow<DownloadEntity?>
    @Query("SELECT * FROM downloads WHERE galleryId = :id") suspend fun get(id: Int): DownloadEntity?
    @Query("SELECT * FROM downloads") suspend fun all(): List<DownloadEntity>
    @Upsert suspend fun upsert(entry: DownloadEntity)
    @Query("UPDATE downloads SET state = 'Downloading', error = NULL WHERE galleryId = :id") suspend fun start(id: Int)
    @Query("UPDATE downloads SET downloadedPages = :pages WHERE galleryId = :id") suspend fun progress(id: Int, pages: Int)
    @Query("UPDATE downloads SET state = 'Completed', downloadedPages = :pages, completedAt = :now, error = NULL WHERE galleryId = :id") suspend fun complete(id: Int, pages: Int, now: Long)
    @Query("UPDATE downloads SET state = 'Failed', error = :error WHERE galleryId = :id") suspend fun fail(id: Int, error: String)
    @Query("UPDATE downloads SET state = 'Queued', error = NULL WHERE galleryId = :id") suspend fun queue(id: Int)
    @Query("DELETE FROM downloads WHERE galleryId = :id") suspend fun delete(id: Int)
    @Query("SELECT COUNT(*) FROM downloads") suspend fun count(): Int
}
