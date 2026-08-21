package com.dublikunt.dmclient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.component.scrollbar.DraggableScrollbar
import com.dublikunt.dmclient.component.scrollbar.rememberDraggableScroller
import com.dublikunt.dmclient.component.scrollbar.scrollbarState
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.status.GalleryStatusBook
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val galleryHistoryDao: GalleryHistoryDao,
    private val statusBook: GalleryStatusBook,
) : ViewModel() {
    val flow = Pager(PagingConfig(pageSize = 25)) {
        galleryHistoryDao.getAllPagingSource()
    }.flow.cachedIn(viewModelScope)

    val statusMap get() = statusBook.statuses

    fun removeGalleryFromHistory(gallery: GalleryHistory) {
        viewModelScope.launch(Dispatchers.IO) { galleryHistoryDao.deleteHistory(gallery) }
    }

    fun loadStatuses(ids: List<Int>) = statusBook.load(ids)
}

@Composable
fun HistoryScreen(
    navController: NavHostController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val items = viewModel.flow.collectAsLazyPagingItems()
    val scrollState = rememberLazyGridState()

    val itemCount = items.itemCount
    LaunchedEffect(itemCount) {
        val ids = (0 until itemCount).mapNotNull { items.peek(it)?.id }
        if (ids.isNotEmpty()) viewModel.loadStatuses(ids)
    }

    val statusMap by viewModel.statusMap.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            columns = GridCells.Adaptive(minSize = 128.dp),
            state = scrollState,
        ) {
            items(count = items.itemCount) { index ->
                val history = items[index]
                history?.let { h ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        GalleryCard(
                            GallerySimpleInfo(h.id, h.coverUrl, h.name),
                            navController,
                            statusMap[h.id]?.name,
                            statusMap[h.id]?.color,
                            statusMap[h.id]?.favorite ?: false
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            IconButton(onClick = { viewModel.removeGalleryFromHistory(h) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                            }
                        }
                    }
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
