package com.dublikunt.dmclient.database.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dublikunt.dmclient.scrapper.ImageType
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "downloaded_galleries")
data class DownloadedGallery(
    @PrimaryKey val id: Int,
    val title: String,
    val coverPath: String,
    val totalPages: Int,
    val pagesId: Int,
    val imageTypes: List<ImageType>,
    val tags: List<String>,
    val artists: List<String>,
    val characters: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
