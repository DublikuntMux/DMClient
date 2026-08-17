package com.dublikunt.dmclient.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.component.LoadingScreen
import com.dublikunt.dmclient.repository.DownloadRepository
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    downloadRepository: DownloadRepository
) : ViewModel() {
    val flow = Pager(PagingConfig(pageSize = 25)) {
        downloadRepository.getAllPagingSource()
    }.flow.cachedIn(viewModelScope)
}

@Composable
fun DownloadScreen(
    navController: NavHostController,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val items = viewModel.flow.collectAsLazyPagingItems()

    when (items.loadState.refresh) {
        is LoadState.Loading -> LoadingScreen()
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(count = items.itemCount) { index ->
                        val gallery = items[index]
                        gallery?.let {
                            GalleryCard(
                                GallerySimpleInfo(it.id, File(it.coverPath).path, it.title),
                                navController, null, null, false
                            )
                        }
                    }
                }
            }
        }
    }
}
