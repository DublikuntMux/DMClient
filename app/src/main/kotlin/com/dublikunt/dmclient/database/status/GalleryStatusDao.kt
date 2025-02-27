package com.dublikunt.dmclient.database.status

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GalleryStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: GalleryStatus)

    @Query("SELECT * FROM gallery_status WHERE id IN (:ids)")
    suspend fun getStatuses(ids: List<Int>): List<GalleryStatus>

    @Query("SELECT * FROM gallery_status WHERE id = :id LIMIT 1")
    suspend fun getStatus(id: Int): GalleryStatus?

    @Delete
    suspend fun deleteState(status: GalleryStatus)
}
