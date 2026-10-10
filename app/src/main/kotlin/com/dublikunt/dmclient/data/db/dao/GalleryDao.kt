package com.dublikunt.dmclient.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dublikunt.dmclient.data.db.entity.GalleryEntity

@Dao
interface GalleryDao {
    @Upsert
    suspend fun upsert(gallery: GalleryEntity)

    @Upsert
    suspend fun upsert(galleries: List<GalleryEntity>)

    @Query("SELECT * FROM galleries WHERE id = :id")
    suspend fun get(id: Int): GalleryEntity?

    @Query("SELECT * FROM galleries")
    suspend fun all(): List<GalleryEntity>
}
