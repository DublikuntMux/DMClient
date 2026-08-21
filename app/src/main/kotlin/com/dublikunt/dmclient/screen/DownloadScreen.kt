package com.dublikunt.dmclient.screen

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.component.GalleryGridSkeleton
import com.dublikunt.dmclient.component.scrollbar.DraggableScrollbar
import com.dublikunt.dmclient.component.scrollbar.rememberDraggableScroller
import com.dublikunt.dmclient.component.scrollbar.scrollbarState
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import com.dublikunt.dmclient.download.GalleryContentLocator
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    downloadedGalleryDao: DownloadedGalleryDao
) : ViewModel() {
    val flow = Pager(PagingConfig(pageSize = 25)) {
        downloadedGalleryDao.getAllPagingSource()
    }.flow.cachedIn(viewModelScope)
}

@Composable
fun DownloadScreen(
    navController: NavHostController,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val items = viewModel.flow.collectAsLazyPagingItems()

    when (items.loadState.refresh) {
        is LoadState.Loading -> GalleryGridSkeleton(minSize = 150.dp)
        is LoadState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load downloads.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { items.retry() }) {
                        Text("Retry")
                    }
                }
            }
        }

        else -> {
            if (items.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloaded galleries found.")
                }
            } else {
                val scrollState = rememberLazyGridState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        state = scrollState
                    ) {
                        items(count = items.itemCount) { index ->
                            val gallery = items[index]
                            gallery?.let {
                                GalleryCard(
                                    GallerySimpleInfo(
                                        it.id,
                                        GalleryContentLocator.resolveCover(context.filesDir, it.coverPath),
                                        it.title
                                    ),
                                    navController, null, null, false
                                )
                            }
                        }
                    }
                    scrollState.DraggableScrollbar(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .align(Alignment.CenterEnd),
                        state = scrollState.scrollbarState(itemsAvailable = items.itemCount),
                        orientation = Orientation.Vertical,
                        onThumbMoved = scrollState.rememberDraggableScroller(
                            itemsAvailable = items.itemCount
                        )
                    )
                }
            }
        }
    }
}
