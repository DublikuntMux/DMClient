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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dublikunt.dmclient.component.GalleryPageCard
import com.dublikunt.dmclient.component.GalleryPageViewer
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.Status
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.scrapper.ImageType
import com.dublikunt.dmclient.scrapper.NHentaiApi
import com.dublikunt.dmclient.work.ArchiveWorker
import com.dublikunt.dmclient.work.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val statusDao = db.galleryStatusDao()
    private val downloadDao = db.downloadedGalleryDao()
    private val workManager = WorkManager.getInstance(application)

    private val _galleryState = MutableStateFlow<GalleryState>(GalleryState.Loading)
    val galleryState: StateFlow<GalleryState> = _galleryState

    fun fetchGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloaded = downloadDao.getById(id)
            val workInfos = workManager.getWorkInfosForUniqueWorkLiveData("download_$id")
            val archiveInfos = workManager.getWorkInfosForUniqueWorkLiveData("archive_$id")

            if (downloaded != null) {
                val status = statusDao.getStatus(id)
                val gallery = GalleryFullInfo(
                    id = downloaded.id,
                    thumb = downloaded.coverPath,
                    name = downloaded.title,
                    tags = downloaded.tags,
                    artists = downloaded.artists,
                    characters = downloaded.characters,
                    pages = downloaded.totalPages,
                    pagesId = downloaded.pagesId,
                    images = downloaded.imageTypes
                )
                _galleryState.value = GalleryState.Success(gallery, status, isDownloaded = true)

                launch(Dispatchers.Main) {
                    archiveInfos.asFlow().collect { infos ->
                        val info = infos.firstOrNull()
                        val isArchiving = info != null && !info.state.isFinished
                        _galleryState.value =
                            (_galleryState.value as? GalleryState.Success)?.copy(isArchiving = isArchiving)
                                ?: _galleryState.value
                    }
                }
            } else {
                val gallery = NHentaiApi.fetchGallery(id)
                if (gallery != null) {
                    val status = statusDao.getStatus(id)
                    _galleryState.value =
                        GalleryState.Success(gallery, status, isDownloaded = false)

                    launch(Dispatchers.Main) {
                        workInfos.asFlow().collect { infos ->
                            val info = infos.firstOrNull()
                            val isDownloading = info != null && !info.state.isFinished
                            _galleryState.value =
                                (_galleryState.value as? GalleryState.Success)?.copy(isDownloading = isDownloading)
                                    ?: _galleryState.value
                        }
                    }
                } else {
                    _galleryState.value =
                        GalleryState.Error("Failed to load gallery. Please try again.")
                }
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

    fun archiveGallery(gallery: GalleryFullInfo) {
        val galleryJson = Json.encodeToString(gallery)
        val workRequest = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setInputData(workDataOf(ArchiveWorker.KEY_GALLERY_JSON to galleryJson))
            .addTag("archive_${gallery.id}")
            .build()

        workManager.enqueueUniqueWork(
            "archive_${gallery.id}",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )

        _galleryState.value =
            (_galleryState.value as? GalleryState.Success)?.copy(isArchiving = true)
                ?: return

        viewModelScope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(workRequest.id).asFlow().collect { info ->
                if (info != null && info.state.isFinished) {
                    _galleryState.value = (_galleryState.value as? GalleryState.Success)?.copy(
                        isArchiving = false
                    ) ?: _galleryState.value
                }
            }
        }
    }

    fun downloadGallery(gallery: GalleryFullInfo) {
        val galleryJson = Json.encodeToString(gallery)
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_GALLERY_JSON to galleryJson))
            .addTag("download_${gallery.id}")
            .build()

        workManager.enqueueUniqueWork(
            "download_${gallery.id}",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )

        _galleryState.value =
            (_galleryState.value as? GalleryState.Success)?.copy(isDownloading = true)
                ?: return

        viewModelScope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(workRequest.id).asFlow().collect { info ->
                if (info != null && info.state == WorkInfo.State.SUCCEEDED) {
                    _galleryState.value = (_galleryState.value as? GalleryState.Success)?.copy(
                        isDownloading = false,
                        isDownloaded = true
                    ) ?: _galleryState.value
                } else if (info != null && info.state == WorkInfo.State.FAILED) {
                    _galleryState.value = (_galleryState.value as? GalleryState.Success)?.copy(
                        isDownloading = false
                    ) ?: _galleryState.value
                }
            }
        }
    }

    fun deleteGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _galleryState.value =
                (_galleryState.value as? GalleryState.Success)?.copy(isDownloading = true)
                    ?: return@launch

            workManager.cancelUniqueWork("download_$id")

            val context = getApplication<Application>()
            val galleryDir = File(context.filesDir, "galleries/$id")
            if (galleryDir.exists()) {
                galleryDir.deleteRecursively()
            }

            val downloaded = downloadDao.getById(id)
            if (downloaded != null) {
                downloadDao.delete(downloaded)
            }

            _galleryState.value = (_galleryState.value as? GalleryState.Success)?.copy(
                isDownloading = false,
                isDownloaded = false
            ) ?: _galleryState.value
        }
    }
}

sealed class GalleryState {
    data object Loading : GalleryState()
    data class Success(
        val gallery: GalleryFullInfo,
        val status: GalleryStatus?,
        val selectedPage: Int? = null,
        val isDownloaded: Boolean = false,
        val isDownloading: Boolean = false,
        val isArchiving: Boolean = false
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
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading...", textAlign = TextAlign.Center)
                }
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
            val isDownloaded = state.isDownloaded
            val isDownloading = state.isDownloading
            val isArchiving = state.isArchiving

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusControls(status, onUpdateStatus = { newStatus, isFav ->
                                viewModel.updateStatus(id, newStatus, isFav)
                            })

                            if (isDownloading) {
                                CircularProgressIndicator()
                            } else {
                                Row {
                                    if (isDownloaded) {
                                        if (isArchiving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .padding(8.dp)
                                            )
                                        } else {
                                            IconButton(
                                                onClick = { viewModel.archiveGallery(gallery) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Save,
                                                    contentDescription = "Archive"
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isDownloaded) {
                                                viewModel.deleteGallery(gallery.id)
                                            } else {
                                                viewModel.downloadGallery(gallery)
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Rounded.Delete else Icons.Rounded.Download,
                                            contentDescription = if (isDownloaded) "Delete" else "Download"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(gallery.pages) { pageIndex ->
                    val imageUrl = if (isDownloaded) {
                        val imageType = gallery.images[pageIndex]
                        val ext = when (imageType) {
                            ImageType.Jpg -> "jpg"
                            ImageType.Webp -> "webp"
                        }
                        File(
                            context.filesDir,
                            "galleries/${gallery.id}/${pageIndex + 1}.$ext"
                        ).absolutePath
                    } else {
                        val imageType = gallery.images[pageIndex]
                        when (imageType) {
                            ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery.pagesId}/${pageIndex + 1}.jpg"
                            ImageType.Webp -> "https://i1.nhentai.net/galleries/${gallery.pagesId}/${pageIndex + 1}.webp"
                        }
                    }

                    GalleryPageCard(imageUrl, pageIndex + 1) {
                        viewModel.selectPage(pageIndex + 1)
                    }
                }
            }

            selectedPage?.let { currentPage ->
                val imageUrl = if (isDownloaded) {
                    val imageType = gallery.images[currentPage - 1]
                    val ext = when (imageType) {
                        ImageType.Jpg -> "jpg"
                        ImageType.Webp -> "webp"
                    }
                    File(
                        context.filesDir,
                        "galleries/${gallery.id}/$currentPage.$ext"
                    ).absolutePath
                } else {
                    val imageType = gallery.images[currentPage - 1]
                    when (imageType) {
                        ImageType.Jpg -> "https://i1.nhentai.net/galleries/${gallery.pagesId}/${currentPage}.jpg"
                        ImageType.Webp -> "https://i1.nhentai.net/galleries/${gallery.pagesId}/${currentPage}.webp"
                    }
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
