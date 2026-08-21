package com.dublikunt.dmclient.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.ErrorScreen
import com.dublikunt.dmclient.component.GalleryDetailSkeleton
import com.dublikunt.dmclient.component.GalleryImage
import com.dublikunt.dmclient.component.GalleryPageCard
import com.dublikunt.dmclient.component.GalleryPageViewer
import com.dublikunt.dmclient.component.StatusColorPicker
import com.dublikunt.dmclient.component.scrollbar.DraggableScrollbar
import com.dublikunt.dmclient.component.scrollbar.rememberDraggableScroller
import com.dublikunt.dmclient.component.scrollbar.scrollbarState
import com.dublikunt.dmclient.database.status.CustomStatus
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import com.dublikunt.dmclient.download.DownloadController
import com.dublikunt.dmclient.download.DownloadPhase
import com.dublikunt.dmclient.download.DownloadedGalleryStore
import com.dublikunt.dmclient.download.GalleryContentLocator
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.scrapper.NHentaiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val nHentaiApi: NHentaiApi,
    private val statusDao: GalleryStatusDao,
    private val downloadedStore: DownloadedGalleryStore,
    private val downloadController: DownloadController
) : ViewModel() {
    private val _galleryState = MutableStateFlow<GalleryState>(GalleryState.Loading)
    val galleryState: StateFlow<GalleryState> = _galleryState.asStateFlow()

    private var downloadJob: Job? = null
    private var archiveJob: Job? = null

    private fun updateSuccessState(update: (GalleryState.Success) -> GalleryState.Success) {
        val current = _galleryState.value
        if (current is GalleryState.Success) _galleryState.value = update(current)
    }

    fun fetchGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloaded = downloadedStore.getById(id)
            if (downloaded != null) {
                val status = statusDao.getStatus(id)
                val statuses = statusDao.getCustomStatuses()
                val gallery = GalleryFullInfo(
                    id = downloaded.id,
                    thumb = downloadedStore.resolveCover(downloaded.coverPath),
                    name = downloaded.title,
                    parodies = downloaded.parodies, tags = downloaded.tags,
                    artists = downloaded.artists, characters = downloaded.characters,
                    pages = downloaded.totalPages, pagesId = downloaded.pagesId,
                    images = downloaded.imageTypes
                )
                _galleryState.value =
                    GalleryState.Success(gallery, status, statuses, isDownloaded = true)
            } else {
                val gallery = nHentaiApi.fetchGallery(id)
                if (gallery != null) {
                    val status = statusDao.getStatus(id)
                    val statuses = statusDao.getCustomStatuses()
                    _galleryState.value = GalleryState.Success(gallery, status, statuses)
                    watchDownload(id)
                } else {
                    _galleryState.value =
                        GalleryState.Error("Failed to load gallery. Please try again.")
                }
            }
        }
    }

    private fun watchDownload(id: Int) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadController.observe(id).collect { phase ->
                when (phase) {
                    DownloadPhase.Succeeded -> updateSuccessState {
                        it.copy(isDownloading = false, isDownloaded = true)
                    }

                    DownloadPhase.Failed, DownloadPhase.Idle -> updateSuccessState {
                        it.copy(isDownloading = false)
                    }

                    DownloadPhase.Running -> updateSuccessState {
                        it.copy(isDownloading = true)
                    }
                }
            }
        }
    }

    fun updateStatus(id: Int, newStatusId: Int?, isFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.insertStatus(GalleryStatus(id, newStatusId, isFavorite))
            val updatedStatus = statusDao.getStatus(id)
            updateSuccessState { it.copy(status = updatedStatus) }
        }
    }

    fun createCustomStatus(name: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.insertCustomStatus(CustomStatus(name = name, color = color))
            val statuses = statusDao.getCustomStatuses()
            updateSuccessState { it.copy(availableStatuses = statuses) }
        }
    }

    fun updateCustomStatus(status: CustomStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.updateCustomStatus(status)
            val statuses = statusDao.getCustomStatuses()
            val current = (_galleryState.value as? GalleryState.Success)?.gallery?.id
            val refreshed = current?.let { statusDao.getStatus(it) }
            updateSuccessState { it.copy(status = refreshed, availableStatuses = statuses) }
        }
    }

    fun selectPage(page: Int?) {
        updateSuccessState { it.copy(selectedPage = page) }
    }

    fun archiveGallery(gallery: GalleryFullInfo) {
        archiveJob?.cancel()
        downloadController.archive(gallery.id, gallery.name)
        updateSuccessState { it.copy(isArchiving = true) }
        archiveJob = viewModelScope.launch {
            downloadController.observeArchive(gallery.id).collect { phase ->
                when (phase) {
                    DownloadPhase.Succeeded, DownloadPhase.Failed ->
                        updateSuccessState { it.copy(isArchiving = false) }

                    else -> {}
                }
            }
        }
    }

    fun downloadGallery(gallery: GalleryFullInfo) {
        watchDownload(gallery.id)
        updateSuccessState { it.copy(isDownloading = true) }
        viewModelScope.launch { downloadController.start(gallery) }
    }

    fun deleteGallery(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            updateSuccessState { it.copy(isDownloading = true) }
            downloadController.delete(id)
            updateSuccessState { it.copy(isDownloading = false, isDownloaded = false) }
        }
    }
}

sealed class GalleryState {
    data object Loading : GalleryState()
    data class Success(
        val gallery: GalleryFullInfo,
        val status: GalleryStatusWithCustomStatus?,
        val availableStatuses: List<CustomStatus> = emptyList(),
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
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val galleryState by viewModel.galleryState.collectAsState()
    val scrollState = rememberLazyListState()

    val onTagClick: (String) -> Unit = remember(navController) {
        { name -> navController.navigate("search?query=${Uri.encode(name)}") }
    }

    LaunchedEffect(id) { viewModel.fetchGallery(id) }

    when (val state = galleryState) {
        is GalleryState.Loading -> GalleryDetailSkeleton()
        is GalleryState.Error -> ErrorScreen(state.message) { viewModel.fetchGallery(id) }
        is GalleryState.Success -> {
            val gallery = state.gallery
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    state = scrollState
                ) {
                    item {
                        GalleryHeader(
                            state = state,
                            onUpdateStatus = { newStatusId, isFav ->
                                viewModel.updateStatus(
                                    id,
                                    newStatusId,
                                    isFav
                                )
                            },
                            onCreateStatus = { name, color ->
                                viewModel.createCustomStatus(
                                    name,
                                    color
                                )
                            },
                            onEditStatus = { viewModel.updateCustomStatus(it) },
                            onArchive = { viewModel.archiveGallery(gallery) },
                            onDownloadOrDelete = {
                                if (state.isDownloaded) viewModel.deleteGallery(gallery.id)
                                else viewModel.downloadGallery(gallery)
                            },
                            onTagClick = onTagClick
                        )
                    }
                    items(count = gallery.pages, key = { it }) { pageIndex ->
                        GalleryPageCard(
                            getImageUrl(context, gallery, pageIndex + 1, state.isDownloaded),
                            pageIndex + 1
                        ) { viewModel.selectPage(pageIndex + 1) }
                    }
                }
                val itemsAvailable = gallery.pages + 1
                scrollState.DraggableScrollbar(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .align(Alignment.CenterEnd),
                    state = scrollState.scrollbarState(itemsAvailable = itemsAvailable),
                    orientation = Orientation.Vertical,
                    onThumbMoved = scrollState.rememberDraggableScroller(
                        itemsAvailable = itemsAvailable
                    )
                )
            }
            state.selectedPage?.let { currentPage ->
                BackHandler { viewModel.selectPage(null) }
                GalleryPageViewer(
                    getImageUrl(context, gallery, currentPage, state.isDownloaded),
                    currentPage, gallery.pages,
                    onClose = { viewModel.selectPage(null) },
                    onNextPage = { if (currentPage < gallery.pages) viewModel.selectPage(currentPage + 1) },
                    onPreviousPage = { if (currentPage > 1) viewModel.selectPage(currentPage - 1) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryHeader(
    state: GalleryState.Success,
    onUpdateStatus: (Int?, Boolean) -> Unit,
    onCreateStatus: (String, Int) -> Unit,
    onEditStatus: (CustomStatus) -> Unit,
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
        GalleryImage(
            model = gallery.thumb,
            contentDescription = gallery.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            gallery.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        listOf(
            "Parodies" to gallery.parodies,
            "Tags" to gallery.tags,
            "Characters" to gallery.characters,
            "Artists" to gallery.artists
        ).forEach { (title, items) ->
            if (items.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                FlowRow { items.forEach { TextButton(onClick = { onTagClick(it) }) { Text(it) } } }
            }
        }
        Text("Pages: ${gallery.pages}", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusControls(
                state.status,
                state.availableStatuses,
                onUpdateStatus,
                onCreateStatus,
                onEditStatus
            )
            if (state.isDownloading) {
                CircularProgressIndicator()
            } else {
                Row {
                    if (state.isDownloaded) {
                        if (state.isArchiving) CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(8.dp)
                        )
                        else IconButton(
                            onClick = onArchive,
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Save, "Archive") }
                    }
                    IconButton(onClick = onDownloadOrDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (state.isDownloaded) Icons.Rounded.Delete else Icons.Rounded.Download,
                            if (state.isDownloaded) "Delete" else "Download"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusControls(
    status: GalleryStatusWithCustomStatus?,
    availableStatuses: List<CustomStatus>,
    onUpdateStatus: (Int?, Boolean) -> Unit,
    onCreateStatus: (String, Int) -> Unit,
    onEditStatus: (CustomStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingStatus by remember { mutableStateOf<CustomStatus?>(null) }
    var statusNameInput by remember(editorOpen, editingStatus) {
        mutableStateOf(
            editingStatus?.name ?: ""
        )
    }
    var selectedStatusColor by remember(
        editorOpen,
        editingStatus
    ) { mutableIntStateOf(editingStatus?.color ?: 0x00FF00) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilledTonalButton(onClick = { expanded = true }) {
                Text(
                    status?.status?.name ?: "Set Status"
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableStatuses.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = {
                            onUpdateStatus(
                                s.id,
                                status?.galleryStatus?.favorite ?: false
                            ); expanded = false
                        })
                }
                DropdownMenuItem(
                    text = { Text("Create Status") },
                    onClick = { editingStatus = null; editorOpen = true; expanded = false })
                if (status?.status != null) DropdownMenuItem(
                    text = { Text("Edit Current Status") },
                    onClick = {
                        editingStatus = status.status; editorOpen = true; expanded = false
                    })
                DropdownMenuItem(
                    text = { Text("Remove Status") },
                    onClick = {
                        onUpdateStatus(
                            null,
                            status?.galleryStatus?.favorite ?: false
                        ); expanded = false
                    })
            }
        }
        IconButton(
            onClick = {
                onUpdateStatus(
                    status?.status?.id,
                    !(status?.galleryStatus?.favorite ?: false)
                )
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = if (status?.galleryStatus?.favorite == true) Color.Yellow else Color.Gray),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (status?.galleryStatus?.favorite == true) Icons.Rounded.Star else Icons.Outlined.Star,
                "Favorite"
            )
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editingStatus != null) "Edit Status" else "Create Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = statusNameInput,
                        onValueChange = { statusNameInput = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    StatusColorPicker(selectedStatusColor) { selectedStatusColor = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (statusNameInput.isNotBlank()) {
                        if (editingStatus != null) onEditStatus(
                            editingStatus!!.copy(
                                name = statusNameInput.trim(),
                                color = selectedStatusColor
                            )
                        )
                        else onCreateStatus(statusNameInput.trim(), selectedStatusColor)
                        editorOpen = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("Cancel") } }
        )
    }
}

private fun getImageUrl(
    context: Context,
    gallery: GalleryFullInfo,
    pageNumber: Int,
    isDownloaded: Boolean
): String =
    if (isDownloaded) GalleryContentLocator.localPageAbsolutePath(
        context.filesDir, gallery.id, pageNumber, gallery.images
    )
    else GalleryContentLocator.remotePageUrl(gallery.pagesId, pageNumber, gallery.images)
