package com.dublikunt.dmclient.database.status

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "gallery_status")
data class GalleryStatus(
    @PrimaryKey val id: Int,
    val statusId: Int?,
    val favorite: Boolean
)

@Serializable
@Entity(tableName = "custom_status")
data class CustomStatus(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Long
)

data class GalleryStatusWithCustomStatus(
    @Embedded val galleryStatus: GalleryStatus,
    @Relation(parentColumn = "statusId", entityColumn = "id")
    val status: CustomStatus?
)
