package com.dublikunt.dmclient.data.repository

import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.network.TagType
import java.io.File

sealed interface LibraryFilter {
    data object Favorites : LibraryFilter
    data class Status(val id: Int) : LibraryFilter
    data object AllTracked : LibraryFilter
}

data class ReadingStatus(val id: Int, val name: String, val color: Int)
data class GalleryMark(val favorite: Boolean, val status: ReadingStatus?)
data class LibraryItem(val gallery: GallerySummary, val mark: GalleryMark, val updatedAt: Long)
data class HistoryItem(val gallery: GallerySummary, val lastPage: Int, val pageCount: Int, val openedAt: Long)
enum class DownloadState { Queued, Downloading, Completed, Failed }
data class DownloadItem(
    val gallery: GallerySummary,
    val coverFile: File,
    val state: DownloadState,
    val downloadedPages: Int,
    val pageCount: Int,
    val error: String?,
    val createdAt: Long,
)
sealed interface ArchiveState {
    data object Queued : ArchiveState
    data object Running : ArchiveState
    data class Completed(val uri: String) : ArchiveState
    data class Failed(val error: String) : ArchiveState
}
data class SearchDataStatus(
    val counts: Map<TagType, Int>,
    val lastUpdated: Long?,
    val refreshing: Boolean,
    val failed: Boolean,
)
data class ImportSummary(val galleries: Int, val library: Int, val statuses: Int, val history: Int)
data class StorageUsage(
    val imageCacheBytes: Long,
    val downloadsBytes: Long,
    val downloadsCount: Int,
    val historyCount: Int,
    val searchEntries: Int,
)
