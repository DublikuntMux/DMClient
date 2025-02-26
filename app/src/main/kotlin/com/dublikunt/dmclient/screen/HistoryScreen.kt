package com.dublikunt.dmclient.screen

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val historyDao = db.galleryHistoryDao()
    private val statusDao = db.galleryStatusDao()

    val historyList: LiveData<List<GalleryHistory>> = historyDao.getHistory().asLiveData()
    val statusMap = mutableStateMapOf<Int, GalleryStatus?>()

    fun removeGalleryFromHistory(gallery: GalleryHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteHistory(gallery)
        }
    }

    fun fetchStatuses(ids: List<GalleryHistory>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                val status = statusDao.getStatus(id.id)
                statusMap[id.id] = status
            }
        }
    }
}

@Composable
fun HistoryScreen(navController: NavHostController, viewModel: HistoryViewModel = viewModel()) {
    val historyList by viewModel.historyList.observeAsState(emptyList())
    val scrollState = rememberLazyGridState()

    LaunchedEffect(historyList) {
        viewModel.fetchStatuses(historyList)
    }

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        columns = GridCells.Adaptive(minSize = 128.dp),
        state = scrollState,
    ) {
        historyList.reversed().forEach { galleryHistory ->
            item {
                Box(modifier = Modifier.fillMaxSize()) {
                    GalleryCard(
                        GallerySimpleInfo(
                            galleryHistory.id,
                            galleryHistory.coverUrl,
                            galleryHistory.name
                        ),
                        navController,
                        viewModel.statusMap[galleryHistory.id]?.status,
                        viewModel.statusMap[galleryHistory.id]?.favorite ?: false
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        IconButton(
                            onClick = { viewModel.removeGalleryFromHistory(galleryHistory) }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
