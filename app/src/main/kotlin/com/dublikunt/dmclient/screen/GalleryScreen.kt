package com.dublikunt.dmclient.screen

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dublikunt.dmclient.component.GalleryPageCard
import com.dublikunt.dmclient.component.GalleryPageViewer
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.Status
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

    private val _gallery = MutableStateFlow<GalleryFullInfo?>(null)
    val gallery: StateFlow<GalleryFullInfo?> = _gallery

    private val _selectedPage = MutableStateFlow<Int?>(null)
    val selectedPage: StateFlow<Int?> = _selectedPage

    private val _status = MutableStateFlow<GalleryStatus?>(null)
    val status: StateFlow<GalleryStatus?> = _status

    fun fetchGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _gallery.value = NHentaiApi.fetchGallery(id)
            _status.value = statusDao.getStatus(id)
        }
    }

    fun updateStatus(id: Int, newStatus: Status?, isFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedStatus = GalleryStatus(id, newStatus, isFavorite)
            statusDao.insertStatus(updatedStatus)
            _status.value = updatedStatus
        }
    }

    fun selectPage(page: Int?) {
        _selectedPage.value = page
    }
}

@Composable
fun GalleryScreen(id: Int, viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val gallery by viewModel.gallery.collectAsState()
    val selectedPage by viewModel.selectedPage.collectAsState()
    val status by viewModel.status.collectAsState()

    LaunchedEffect(id) {
        viewModel.fetchGallery(id)
    }

    if (gallery == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                item {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(gallery!!.thumb)
                            .crossfade(true)
                            .build(),
                        contentDescription = gallery!!.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = gallery!!.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pages: ${gallery!!.pages}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        StatusControls(status, onUpdateStatus = { newStatus, isFav ->
                            viewModel.updateStatus(id, newStatus, isFav)
                        })
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(gallery!!.pages) { pageIndex ->
                    val imageUrl = when (gallery!!.imageType)
                    {
                        ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery!!.pagesId}/${pageIndex + 1}.jpg"
                        ImageType.Webp -> "https://i4.nhentai.net/galleries/${gallery!!.pagesId}/${pageIndex + 1}.webp"
                    }

                    GalleryPageCard(imageUrl, pageIndex + 1) {
                        viewModel.selectPage(pageIndex + 1)
                    }
                }
            }

            selectedPage?.let { currentPage ->
                val imageUrl = when (gallery!!.imageType)
                {
                    ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery!!.pagesId}/${currentPage}.jpg"
                    ImageType.Webp -> "https://i4.nhentai.net/galleries/${gallery!!.pagesId}/${currentPage}.webp"
                }

                BackHandler { viewModel.selectPage(null) }

                GalleryPageViewer(
                    imageUrl = imageUrl,
                    pageIndex = currentPage,
                    totalPages = gallery!!.pages,
                    onClose = { viewModel.selectPage(null) },
                    onNextPage = {
                        if (currentPage < gallery!!.pages) viewModel.selectPage(currentPage + 1)
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
