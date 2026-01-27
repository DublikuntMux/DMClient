package com.dublikunt.dmclient.screen

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.TextButton
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
import androidx.work.ExistingWorkPolicy
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

    private fun updateSuccessState(update: (GalleryState.Success) -> GalleryState.Success) {
        val current = _galleryState.value
        if (current is GalleryState.Success) {
            _galleryState.value = update(current)
        }
    }

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
                        val isArchiving = infos.firstOrNull()?.let { !it.state.isFinished } ?: false
                        updateSuccessState { it.copy(isArchiving = isArchiving) }
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
                            val isDownloading =
                                infos.firstOrNull()?.let { !it.state.isFinished } ?: false
                            updateSuccessState { it.copy(isDownloading = isDownloading) }
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
            updateSuccessState { it.copy(status = updatedStatus) }
        }
    }

    fun selectPage(page: Int?) {
        updateSuccessState { it.copy(selectedPage = page) }
    }

    fun archiveGallery(gallery: GalleryFullInfo) {
        val galleryJson = Json.encodeToString(gallery)
        val workRequest = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setInputData(workDataOf(ArchiveWorker.KEY_GALLERY_JSON to galleryJson))
            .addTag("archive_${gallery.id}")
            .build()

        workManager.enqueueUniqueWork("archive_${gallery.id}", ExistingWorkPolicy.KEEP, workRequest)

        updateSuccessState { it.copy(isArchiving = true) }

        viewModelScope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(workRequest.id).asFlow().collect { info ->
                if (info?.state?.isFinished == true) {
                    updateSuccessState { it.copy(isArchiving = false) }
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
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        updateSuccessState { it.copy(isDownloading = true) }

        viewModelScope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(workRequest.id).asFlow().collect { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        updateSuccessState { it.copy(isDownloading = false, isDownloaded = true) }
                    }

                    WorkInfo.State.FAILED -> {
                        updateSuccessState { it.copy(isDownloading = false) }
                    }

                    else -> {}
                }
            }
        }
    }

    fun deleteGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            updateSuccessState { it.copy(isDownloading = true) }

            workManager.cancelUniqueWork("download_$id")

            val context = getApplication<Application>()
            val galleryDir = File(context.filesDir, "galleries/$id")
            if (galleryDir.exists()) {
                galleryDir.deleteRecursively()
            }

            downloadDao.getById(id)?.let { downloadDao.delete(it) }
            updateSuccessState { it.copy(isDownloading = false, isDownloaded = false) }
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

@Composable
fun GalleryScreen(
    id: Int,
    navController: NavHostController,
    viewModel: GalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val galleryState by viewModel.galleryState.collectAsState()
    val scrollState = rememberLazyListState()

    val onTagClick: (String) -> Unit = remember(navController) {
        { name ->
            val formatted = name.replace(" ", "+")
            navController.navigate("search?query=${formatted}")
        }
    }

    LaunchedEffect(id) {
        viewModel.fetchGallery(id)
    }

    when (val state = galleryState) {
        is GalleryState.Loading -> LoadingScreen()
        is GalleryState.Error -> ErrorScreen(state.message) { viewModel.fetchGallery(id) }
        is GalleryState.Success -> {
            val gallery = state.gallery
            val selectedPage = state.selectedPage

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
                state = scrollState
            ) {
                item {
                    GalleryHeader(
                        state = state,
                        onUpdateStatus = { newStatus, isFav ->
                            viewModel.updateStatus(
                                id,
                                newStatus,
                                isFav
                            )
                        },
                        onArchive = { viewModel.archiveGallery(gallery) },
                        onDownloadOrDelete = {
                            if (state.isDownloaded) viewModel.deleteGallery(gallery.id)
                            else viewModel.downloadGallery(gallery)
                        },
                        onTagClick = onTagClick
                    )
                }

                items(gallery.pages) { pageIndex ->
                    val imageUrl = remember(gallery, pageIndex, state.isDownloaded) {
                        getImageUrl(context, gallery, pageIndex + 1, state.isDownloaded)
                    }
                    GalleryPageCard(imageUrl, pageIndex + 1) {
                        viewModel.selectPage(pageIndex + 1)
                    }
                }
            }

            selectedPage?.let { currentPage ->
                val imageUrl = remember(gallery, currentPage, state.isDownloaded) {
                    getImageUrl(context, gallery, currentPage, state.isDownloaded)
                }
                BackHandler { viewModel.selectPage(null) }
                GalleryPageViewer(
                    imageUrl = imageUrl,
                    pageIndex = currentPage,
                    totalPages = gallery.pages,
                    onClose = { viewModel.selectPage(null) },
                    onNextPage = { if (currentPage < gallery.pages) viewModel.selectPage(currentPage + 1) },
                    onPreviousPage = { if (currentPage > 1) viewModel.selectPage(currentPage - 1) }
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Loading...", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryHeader(
    state: GalleryState.Success,
    onUpdateStatus: (Status?, Boolean) -> Unit,
    onArchive: () -> Unit,
    onDownloadOrDelete: () -> Unit,
    onTagClick: (String) -> Unit
) {
    val gallery = state.gallery
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(gallery.thumb).build(),
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

        GallerySection("Tags:", gallery.tags, onTagClick)
        GallerySection("Characters:", gallery.characters, onTagClick)
        GallerySection("Artists:", gallery.artists, onTagClick)

        Text(text = "Pages: ${gallery.pages}", style = MaterialTheme.typography.bodyMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusControls(state.status, onUpdateStatus)

            if (state.isDownloading) {
                CircularProgressIndicator()
            } else {
                Row {
                    if (state.isDownloaded) {
                        if (state.isArchiving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(8.dp)
                            )
                        } else {
                            IconButton(onClick = onArchive, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Save, contentDescription = "Archive")
                            }
                        }
                    }
                    IconButton(onClick = onDownloadOrDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (state.isDownloaded) Icons.Rounded.Delete else Icons.Rounded.Download,
                            contentDescription = if (state.isDownloaded) "Delete" else "Download"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GallerySection(title: String, items: List<String>, onItemClick: (String) -> Unit) {
    if (items.isNotEmpty()) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        FlowRow {
            items.forEach { item ->
                TextButton(onClick = { onItemClick(item) }) {
                    Text(item)
                }
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

private fun getImageUrl(
    context: Context,
    gallery: GalleryFullInfo,
    pageNumber: Int,
    isDownloaded: Boolean
): String {
    val ext = when (gallery.images[pageNumber - 1]) {
        ImageType.Jpg -> "jpg"
        ImageType.Webp -> "webp"
    }
    return if (isDownloaded) {
        File(context.filesDir, "galleries/${gallery.id}/$pageNumber.$ext").absolutePath
    } else {
        "https://i1.nhentai.net/galleries/${gallery.pagesId}/$pageNumber.$ext"
    }
}
