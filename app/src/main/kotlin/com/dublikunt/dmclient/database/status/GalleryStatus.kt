package com.dublikunt.dmclient.database.status

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Status {
    Reading,
    Read
}

@Entity(tableName = "gallery_status")
data class GalleryStatus(
    @PrimaryKey val id: Int,
    val status: Status?,
    val favorite: Boolean
)
