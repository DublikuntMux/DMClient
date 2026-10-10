package com.dublikunt.dmclient.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dublikunt.dmclient.ui.browse.BrowseScreen
import com.dublikunt.dmclient.ui.downloads.DownloadsScreen
import com.dublikunt.dmclient.ui.gallery.GalleryScreen
import com.dublikunt.dmclient.ui.library.LibraryScreen
import com.dublikunt.dmclient.ui.reader.ReaderScreen
import com.dublikunt.dmclient.ui.search.SearchScreen
import com.dublikunt.dmclient.ui.settings.SettingsScreen
import com.dublikunt.dmclient.ui.settings.StatusesScreen
import com.dublikunt.dmclient.ui.settings.StorageScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val openGallery: (Int) -> Unit = { navController.navigate(GalleryRoute(it)) }

    NavHost(navController, startDestination = BrowseRoute, modifier = modifier) {
        composable<BrowseRoute> { BrowseScreen(onOpenGallery = openGallery) }
        composable<SearchRoute> { SearchScreen(onOpenGallery = openGallery) }
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenGallery = openGallery,
                onContinue = { navController.navigate(ReaderRoute(it)) },
                onManageStatuses = { navController.navigate(StatusesRoute) }
            )
        }
        composable<DownloadsRoute> { DownloadsScreen(onOpenGallery = openGallery) }
        composable<SettingsRoute> {
            SettingsScreen(
                onOpenStorage = { navController.navigate(StorageRoute) },
                onOpenStatuses = { navController.navigate(StatusesRoute) }
            )
        }
        composable<StorageRoute> { StorageScreen(onBack = navController::navigateUp) }
        composable<StatusesRoute> { StatusesScreen(onBack = navController::navigateUp) }
        composable<GalleryRoute> {
            GalleryScreen(
                onBack = navController::navigateUp,
                onRead = { id, page -> navController.navigate(ReaderRoute(id, page)) },
                onSearchTag = { tag ->
                    navController.navigate(SearchRoute(tagType = tag.type, tagName = tag.name))
                }
            )
        }
        composable<ReaderRoute> { ReaderScreen(onBack = navController::navigateUp) }
    }
}
