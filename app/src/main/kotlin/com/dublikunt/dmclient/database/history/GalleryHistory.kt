package com.dublikunt.dmclient.database.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_history")
data class GalleryHistory(
    @PrimaryKey val id: Int,
    val coverUrl: String,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)
