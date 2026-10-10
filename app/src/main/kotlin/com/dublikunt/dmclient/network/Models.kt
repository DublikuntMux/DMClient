package com.dublikunt.dmclient.network

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
enum class ContentLanguage(val key: String) {
    All("all"), English("english"), Japanese("japanese"), Chinese("chinese")
}

enum class SortOrder(val key: String?) {
    Recent(null), PopularToday("popular-today"), PopularWeek("popular-week"),
    PopularMonth("popular-month"), PopularAllTime("popular")
}

@Serializable
@Keep
enum class TagType(val key: String) {
    Tag("tag"), Artist("artist"), Character("character"), Parody("parody"),
    Group("group"), Language("language"), Category("category")
}

@Serializable
enum class ImageType { Jpg, Webp, Png }

data class GallerySummary(val id: Int, val title: String, val coverUrl: String)

@Serializable
data class Tag(val type: TagType, val name: String, val count: Int = 0)

@Serializable
data class GalleryDetail(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val coverUrl: String,
    val mediaId: Int,
    val pageCount: Int,
    val pageTypes: List<ImageType>,
    val tags: List<Tag>,
    val uploadDate: Long?,
    val favorites: Int?,
)

data class SearchRequest(
    val text: String = "",
    val include: List<Tag> = emptyList(),
    val exclude: List<Tag> = emptyList(),
    val language: ContentLanguage = ContentLanguage.All,
    val sort: SortOrder = SortOrder.Recent,
)

data class PageResult<T>(val items: List<T>, val totalPages: Int?)
