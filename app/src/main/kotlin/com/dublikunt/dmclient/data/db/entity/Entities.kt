package com.dublikunt.dmclient.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dublikunt.dmclient.data.repository.DownloadState
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "galleries")
data class GalleryEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val coverUrl: String,
    val pageCount: Int,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "library",
    foreignKeys = [ForeignKey(
        entity = StatusEntity::class, parentColumns = ["id"], childColumns = ["statusId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("statusId"), Index("favorite")],
)
data class LibraryEntity(
    @PrimaryKey val galleryId: Int,
    val statusId: Int?,
    val favorite: Boolean,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Int,
    val position: Int,
)

@Serializable
@Entity(tableName = "history", indices = [Index(value = ["openedAt"], orders = [Index.Order.DESC])])
data class HistoryEntity(@PrimaryKey val galleryId: Int, val lastPage: Int, val openedAt: Long)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val galleryId: Int,
    val detailJson: String,
    val coverPath: String,
    val state: DownloadState,
    val downloadedPages: Int,
    val error: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

@Entity(tableName = "search_entries", primaryKeys = ["type", "name"])
data class SearchEntryEntity(val type: String, val name: String)
