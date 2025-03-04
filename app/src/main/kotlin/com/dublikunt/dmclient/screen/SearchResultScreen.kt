package com.dublikunt.dmclient.screen

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.modifier.verticalScrollbar
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import com.dublikunt.dmclient.scrapper.NHentaiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SearchResultViewModel(application: Application) : AndroidViewModel(application) {
    val stateList = mutableStateListOf<GallerySimpleInfo>()
    private var currentPage by mutableIntStateOf(1)
    var isLoading by mutableStateOf(false)
    private var errorCount by mutableIntStateOf(0)
    var showNothingFoundMessage by mutableStateOf(false)
    private var firstLoaded by mutableStateOf(false)

    private val db = AppDatabase.getDatabase(application)
    private val historyDao = db.galleryHistoryDao()
    private val statusDao = db.galleryStatusDao()

    val statusMap = mutableStateMapOf<Int, GalleryStatus?>()

    fun fetchNextPage(scope: CoroutineScope, query: String) {
        if (isLoading || errorCount >= 5) return
        isLoading = true
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    NHentaiApi.search(query, currentPage)
                }
                if (fetched.isNotEmpty()) {
                    stateList.addAll(fetched)
                    currentPage++
                    fetchStatuses(fetched.map { it.id })
                    errorCount = 0
                    if (!firstLoaded) firstLoaded = true
                } else {
                    errorCount++
                    if (errorCount >= 5) {
                        showNothingFoundMessage = true
                    }
                }
            } catch (e: Exception) {
                errorCount++
                if (errorCount >= 5) {
                    showNothingFoundMessage = true
                }
            }
            isLoading = false
        }
    }

    fun addGalleryToHistory(gallery: GallerySimpleInfo) {
        viewModelScope.launch {
            historyDao.insertHistory(GalleryHistory(gallery.id, gallery.thumb, gallery.name))
        }
    }

    private fun fetchStatuses(ids: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            val statuses = statusDao.getStatuses(ids)
            statuses.forEach { statusMap[it.id] = it }
        }
    }
}

@Composable
fun SearchResultScreen(
    query: String,
    navController: NavHostController,
    viewModel: SearchResultViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyGridState()

    LaunchedEffect(query) {
        if (viewModel.stateList.isEmpty() && !viewModel.showNothingFoundMessage) {
            viewModel.fetchNextPage(scope, query)
        }
    }

    if (viewModel.showNothingFoundMessage) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing found", style = MaterialTheme.typography.headlineLarge)
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScrollbar(scrollState),
            columns = GridCells.Adaptive(minSize = 128.dp),
            state = scrollState
        ) {
            items(viewModel.stateList) { galleryItem ->
                GalleryCard(
                    galleryItem, navController, viewModel.statusMap[galleryItem.id]?.status,
                    viewModel.statusMap[galleryItem.id]?.favorite ?: false
                ) {
                    viewModel.addGalleryToHistory(galleryItem)
                }
            }

            item {
                if (viewModel.isLoading) {
                    CircularProgressIndicator()
                } else {
                    LaunchedEffect(Unit) {
                        viewModel.fetchNextPage(scope, query)
                    }
                }
            }
        }
    }
}
