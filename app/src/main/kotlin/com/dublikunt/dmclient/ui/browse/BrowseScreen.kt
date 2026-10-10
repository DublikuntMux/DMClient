package com.dublikunt.dmclient.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.ui.components.EmptyState
import com.dublikunt.dmclient.ui.components.GalleryAction
import com.dublikunt.dmclient.ui.components.GalleryActionsSheet
import com.dublikunt.dmclient.ui.components.GallerySummaryCard
import com.dublikunt.dmclient.ui.components.PagingGalleryGrid
import com.dublikunt.dmclient.ui.components.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseScreen(onOpenGallery: (Int) -> Unit) {
    val viewModel: BrowseViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val galleries = viewModel.galleriesFlow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val refreshState = rememberPullToRefreshState()
    val refreshing = galleries.loadState.refresh is LoadState.Loading && galleries.itemCount > 0
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var languageMenu by remember { mutableStateOf(false) }
    var selectedGallery by remember { mutableStateOf<GallerySummary?>(null) }
    val showScrollToTop by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val first = layout.visibleItemsInfo.firstOrNull()
            val columns = layout.visibleItemsInfo.count { it.row == first?.row }.coerceAtLeast(1)
            val rowHeight = (first?.size?.height ?: 0) + layout.mainAxisItemSpacing
            val distance = gridState.firstVisibleItemIndex / columns * rowHeight +
                    gridState.firstVisibleItemScrollOffset
            distance > layout.viewportSize.height * 2 && layout.viewportSize.height > 0
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("DMClient") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (settings.language != ContentLanguage.All) {
                        AssistChip(
                            onClick = { languageMenu = true },
                            label = { Text(settings.language.name) }
                        )
                    }
                    Box {
                        IconButton(onClick = { languageMenu = true }) {
                            Icon(Icons.Rounded.Translate, contentDescription = "Content language")
                        }
                        DropdownMenu(
                            expanded = languageMenu,
                            onDismissRequest = { languageMenu = false }
                        ) {
                            ContentLanguage.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.name) },
                                    onClick = {
                                        viewModel.setLanguage(language)
                                        languageMenu = false
                                    },
                                    trailingIcon = {
                                        if (language == settings.language) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = "Selected"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = galleries::refresh,
                state = refreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = refreshState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                PagingGalleryGrid(
                    items = galleries,
                    minCellSize = settings.gridDensity.minCell.dp,
                    state = gridState,
                    errorMessage = { it.userMessage() },
                    emptyContent = { EmptyState(Icons.Rounded.Explore, "Nothing here yet") }
                ) { gallery ->
                    GallerySummaryCard(
                        gallery = gallery,
                        mark = marks[gallery.id],
                        onClick = { onOpenGallery(gallery.id) },
                        onLongClick = { selectedGallery = gallery }
                    )
                }
            }
            AnimatedVisibility(
                visible = showScrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleOut(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { gridState.animateScrollToItem(0) } }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        }
    }

    selectedGallery?.let { gallery ->
        GalleryActionsSheet(
            gallery = gallery,
            onDismiss = { selectedGallery = null },
            actions = listOf(
                GalleryAction(
                    "Download",
                    Icons.Rounded.FileDownload
                ) { viewModel.download(gallery) }
            )
        )
    }
}
