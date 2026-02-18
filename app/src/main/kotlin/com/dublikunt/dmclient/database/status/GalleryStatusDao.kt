package com.dublikunt.dmclient.database.status

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface GalleryStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: GalleryStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatuses(statuses: List<GalleryStatus>)

    @Query("SELECT * FROM gallery_status")
    suspend fun getAllGalleryStatusEntities(): List<GalleryStatus>

    @Transaction
    @Query("SELECT * FROM gallery_status")
    suspend fun getAllStatuses(): List<GalleryStatusWithCustomStatus>

    @Transaction
    @Query("SELECT * FROM gallery_status WHERE id IN (:ids)")
    suspend fun getStatuses(ids: List<Int>): List<GalleryStatusWithCustomStatus>

    @Transaction
    @Query("SELECT * FROM gallery_status WHERE id = :id LIMIT 1")
    suspend fun getStatus(id: Int): GalleryStatusWithCustomStatus?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomStatus(status: CustomStatus): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomStatuses(statuses: List<CustomStatus>)

    @Update
    suspend fun updateCustomStatus(status: CustomStatus)

    @Query("SELECT * FROM custom_status ORDER BY id ASC")
    suspend fun getCustomStatuses(): List<CustomStatus>

    @Query("SELECT * FROM custom_status WHERE id = :id LIMIT 1")
    suspend fun getCustomStatus(id: Int): CustomStatus?

    @Query("UPDATE gallery_status SET statusId = NULL WHERE statusId = :statusId")
    suspend fun clearStatusFromGalleries(statusId: Int)

    @Query("DELETE FROM custom_status WHERE id = :statusId")
    suspend fun deleteCustomStatus(statusId: Int)

    @Delete
    suspend fun deleteState(status: GalleryStatus)
}
