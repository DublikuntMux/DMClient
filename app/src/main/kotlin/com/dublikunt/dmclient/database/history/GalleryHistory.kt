package com.dublikunt.dmclient.database.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "gallery_history",
    indices = [Index(value = ["timestamp"], orders = [Index.Order.DESC])]
)
data class GalleryHistory(
    @PrimaryKey val id: Int,
    val coverUrl: String,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)
