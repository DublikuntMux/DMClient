package com.dublikunt.dmclient.screen

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dublikunt.dmclient.component.GalleryPageCard
import com.dublikunt.dmclient.component.GalleryPageViewer
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.Status
import com.dublikunt.dmclient.modifier.verticalListScrollbar
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.scrapper.ImageType
import com.dublikunt.dmclient.scrapper.NHentaiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val statusDao = db.galleryStatusDao()

    private val _galleryState = MutableStateFlow<GalleryState>(GalleryState.Loading)
    val galleryState: StateFlow<GalleryState> = _galleryState

    fun fetchGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val gallery = NHentaiApi.fetchGallery(id)
            if (gallery != null) {
                val status = statusDao.getStatus(id)
                _galleryState.value = GalleryState.Success(gallery, status)
            } else {
                _galleryState.value =
                    GalleryState.Error("Failed to load gallery. Please try again.")
            }
        }
    }

    fun updateStatus(id: Int, newStatus: Status?, isFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedStatus = GalleryStatus(id, newStatus, isFavorite)
            statusDao.insertStatus(updatedStatus)
            _galleryState.value =
                (_galleryState.value as? GalleryState.Success)?.copy(status = updatedStatus)
                    ?: _galleryState.value
        }
    }

    fun selectPage(page: Int?) {
        _galleryState.value =
            (_galleryState.value as? GalleryState.Success)?.copy(selectedPage = page)
                ?: _galleryState.value
    }
}

sealed class GalleryState {
    data object Loading : GalleryState()
    data class Success(
        val gallery: GalleryFullInfo,
        val status: GalleryStatus?,
        val selectedPage: Int? = null
    ) : GalleryState()

    data class Error(val message: String) : GalleryState()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GalleryScreen(
    id: Int,
    navController: NavHostController,
    viewModel: GalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val galleryState by viewModel.galleryState.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(id) {
        viewModel.fetchGallery(id)
    }

    when (val state = galleryState) {
        is GalleryState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is GalleryState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.fetchGallery(id) }) {
                        Text(text = "Retry")
                    }
                }
            }
        }

        is GalleryState.Success -> {
            val gallery = state.gallery
            val selectedPage = state.selectedPage
            val status = state.status

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalListScrollbar(scrollState)
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.Top,
                state = scrollState
            ) {

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(gallery.thumb)
                                .build(),
                            contentDescription = gallery.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Text(
                            text = gallery.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Tags:",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            gallery.tags.forEach {
                                Text(
                                    text = it,
                                    modifier = Modifier.clickable {
                                        val formatted = it.replace(" ", "+")
                                        navController.navigate("search?query=${formatted}")
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        if (gallery.characters.isNotEmpty()) {
                            Text(
                                text = "Characters:",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                gallery.characters.forEach {
                                    Text(
                                        text = it,
                                        modifier = Modifier.clickable {
                                            val formatted = it.replace(" ", "+")
                                            navController.navigate("search?query=${formatted}")
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }

                        if (gallery.artists.isNotEmpty()) {
                            Text(
                                text = "Artists:",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                gallery.artists.forEach {
                                    Text(
                                        text = it,
                                        modifier = Modifier.clickable {
                                            val formatted = it.replace(" ", "+")
                                            navController.navigate("search?query=${formatted}")
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Pages: ${gallery.pages}",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        StatusControls(status, onUpdateStatus = { newStatus, isFav ->
                            viewModel.updateStatus(id, newStatus, isFav)
                        })
                    }
                }

                items(gallery.pages) { pageIndex ->
                    val imageUrl = when (gallery.imageType) {
                        ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery.pagesId}/${pageIndex + 1}.jpg"
                        ImageType.Webp -> "https://i4.nhentai.net/galleries/${gallery.pagesId}/${pageIndex + 1}.webp"
                    }
                    GalleryPageCard(imageUrl, pageIndex + 1) {
                        viewModel.selectPage(pageIndex + 1)
                    }
                }
            }

            selectedPage?.let { currentPage ->
                val imageUrl = when (gallery.imageType) {
                    ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery.pagesId}/${currentPage}.jpg"
                    ImageType.Webp -> "https://i4.nhentai.net/galleries/${gallery.pagesId}/${currentPage}.webp"
                }

                BackHandler { viewModel.selectPage(null) }

                GalleryPageViewer(
                    imageUrl = imageUrl,
                    pageIndex = currentPage,
                    totalPages = gallery.pages,
                    onClose = { viewModel.selectPage(null) },
                    onNextPage = {
                        if (currentPage < gallery.pages) viewModel.selectPage(currentPage + 1)
                    },
                    onPreviousPage = {
                        if (currentPage > 1) viewModel.selectPage(currentPage - 1)
                    }
                )
            }
        }
    }
}

@Composable
fun StatusControls(status: GalleryStatus?, onUpdateStatus: (Status?, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Button(onClick = { expanded = true }) {
                Text(text = status?.status?.name ?: "Set Status")
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Status.entries.forEach { statusOption ->
                    DropdownMenuItem(text = { Text(statusOption.name) }, onClick = {
                        onUpdateStatus(statusOption, status?.favorite ?: false)
                        expanded = false
                    })
                }
                DropdownMenuItem(text = { Text("Remove Status") }, onClick = {
                    onUpdateStatus(null, status?.favorite ?: false)
                    expanded = false
                })
            }
        }

        IconButton(
            onClick = { onUpdateStatus(status?.status, !(status?.favorite ?: false)) },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (status?.favorite == true) Color.Yellow else Color.Gray
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (status?.favorite == true) Icons.Rounded.Star else Icons.Outlined.Star,
                contentDescription = "Favorite"
            )
        }
    }
}
