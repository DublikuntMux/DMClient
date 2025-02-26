package com.dublikunt.dmclient.database.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: GalleryHistory)

    @Query("SELECT * FROM gallery_history ORDER BY id DESC")
    fun getHistory(): Flow<List<GalleryHistory>>

    @Delete
    suspend fun deleteHistory(galleryHistory: GalleryHistory)
}