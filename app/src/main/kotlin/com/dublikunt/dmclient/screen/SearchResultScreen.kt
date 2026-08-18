package com.dublikunt.dmclient.screen

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.dublikunt.dmclient.component.ErrorScreen
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.component.GalleryGridSkeleton
import com.dublikunt.dmclient.component.GalleryLoadingRowSkeleton
import com.dublikunt.dmclient.component.scrollbar.DraggableScrollbar
import com.dublikunt.dmclient.component.scrollbar.rememberDraggableScroller
import com.dublikunt.dmclient.component.scrollbar.scrollbarState
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import com.dublikunt.dmclient.paging.SearchResultPagingSource
import com.dublikunt.dmclient.repository.HistoryRepository
import com.dublikunt.dmclient.repository.PreferenceRepository
import com.dublikunt.dmclient.repository.SearchRepository
import com.dublikunt.dmclient.scrapper.ContentLanguage
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchResultViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val preferenceRepository: PreferenceRepository,
    private val galleryStatusDao: GalleryStatusDao,
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    private val _language = MutableStateFlow(ContentLanguage.All)
    private val _query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = combine(_query, _language) { q, l ->
        if (q.isEmpty()) null else Pair(q, l)
    }.filterNotNull().distinctUntilChanged().flatMapLatest { (query, lang) ->
        Pager(PagingConfig(pageSize = 25)) {
            SearchResultPagingSource(searchRepository, query, lang)
        }.flow
    }.cachedIn(viewModelScope)

    private val _statusMap = MutableStateFlow<Map<Int, GalleryStatusWithCustomStatus?>>(emptyMap())
    val statusMap: StateFlow<Map<Int, GalleryStatusWithCustomStatus?>> = _statusMap.asStateFlow()

    private val loadedStatusIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            _language.value =
                ContentLanguage.fromString(preferenceRepository.preferredLanguage.first() ?: "all")
        }
    }

    fun setQuery(query: String) {
        if (_query.value != query) {
            loadedStatusIds.clear()
            _statusMap.value = emptyMap()
            _query.value = query
        }
    }

    fun loadStatuses(ids: List<Int>) {
        val newIds = ids.filter { it !in loadedStatusIds }
        if (newIds.isEmpty()) return
        loadedStatusIds.addAll(newIds)
        viewModelScope.launch(Dispatchers.IO) {
            val statuses = galleryStatusDao.getStatuses(newIds)
            _statusMap.value = _statusMap.value + statuses.associateBy { it.galleryStatus.id }
        }
    }

    fun addGalleryToHistory(gallery: GallerySimpleInfo) {
        viewModelScope.launch {
            historyRepository.insertHistory(
                GalleryHistory(
                    gallery.id,
                    gallery.thumb,
                    gallery.name
                )
            )
        }
    }
}

@Composable
fun SearchResultScreen(
    query: String,
    navController: NavHostController,
    viewModel: SearchResultViewModel = hiltViewModel()
) {
    val scrollState = rememberLazyGridState()

    LaunchedEffect(query) { viewModel.setQuery(query) }

    val items = viewModel.flow.collectAsLazyPagingItems()

    val itemCount = items.itemCount
    LaunchedEffect(itemCount) {
        val ids = (0 until itemCount).mapNotNull { items.peek(it)?.id }
        if (ids.isNotEmpty()) viewModel.loadStatuses(ids)
    }

    val statusMap by viewModel.statusMap.collectAsState()

    when (val refresh = items.loadState.refresh) {
        is LoadState.Loading -> GalleryGridSkeleton()
        is LoadState.Error -> ErrorScreen("Failed to load results. Please try again.") {
            items.retry()
        }

        else -> {
            if (items.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing found", style = MaterialTheme.typography.headlineLarge)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        columns = GridCells.Adaptive(minSize = 128.dp),
                        state = scrollState
                    ) {
                        items(count = items.itemCount) { index ->
                            val galleryItem = items[index]
                            galleryItem?.let {
                                GalleryCard(
                                    it, navController,
                                    statusMap[it.id]?.status?.name,
                                    statusMap[it.id]?.status?.color,
                                    statusMap[it.id]?.galleryStatus?.favorite ?: false
                                ) { viewModel.addGalleryToHistory(it) }
                            }
                        }

                        when (val state = items.loadState.append) {
                            is LoadState.Loading -> {
                                item { GalleryLoadingRowSkeleton() }
                            }

                            is LoadState.Error -> item {
                                Text(
                                    "Failed to load more results. Please try again.",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }

                            else -> {}
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
