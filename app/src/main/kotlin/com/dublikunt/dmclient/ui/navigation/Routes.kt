package com.dublikunt.dmclient.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.dublikunt.dmclient.network.TagType
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object BrowseRoute

/** Opens search, optionally pre-filled with free text and/or one include filter. */
@Serializable
data class SearchRoute(
    val text: String = "",
    val tagType: TagType? = null,
    val tagName: String? = null
)

@Serializable
data object LibraryRoute

@Serializable
data object DownloadsRoute

@Serializable
data object SettingsRoute

@Serializable
data object StorageRoute

@Serializable
data object StatusesRoute

@Serializable
data class GalleryRoute(val id: Int)

/** [page] null resumes from the saved history position. */
@Serializable
data class ReaderRoute(val id: Int, val page: Int? = null)

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Browse(BrowseRoute, BrowseRoute::class, "Browse", Icons.Outlined.Explore, Icons.Rounded.Explore),
    Search(SearchRoute(), SearchRoute::class, "Search", Icons.Rounded.Search, Icons.Rounded.Search),
    Library(
        LibraryRoute, LibraryRoute::class, "Library",
        Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary
    ),
    Downloads(
        DownloadsRoute, DownloadsRoute::class, "Downloads",
        Icons.Outlined.FileDownload, Icons.Rounded.FileDownload
    ),
    Settings(
        SettingsRoute, SettingsRoute::class, "Settings",
        Icons.Outlined.Settings, Icons.Rounded.Settings
    ),
}
