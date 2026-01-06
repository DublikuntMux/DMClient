package com.dublikunt.dmclient.database.download

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedGalleryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gallery: DownloadedGallery)

    @Query("SELECT * FROM downloaded_galleries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DownloadedGallery>>

    @Query("SELECT * FROM downloaded_galleries WHERE id = :id")
    suspend fun getById(id: Int): DownloadedGallery?

    @Delete
    suspend fun delete(gallery: DownloadedGallery)

    @Query("DELETE FROM downloaded_galleries")
    suspend fun deleteAll()
}
