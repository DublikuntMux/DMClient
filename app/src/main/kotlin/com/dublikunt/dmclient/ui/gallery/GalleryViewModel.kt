package com.dublikunt.dmclient.ui.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.dublikunt.dmclient.data.repository.ArchiveState
import com.dublikunt.dmclient.data.repository.DownloadItem
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.repository.GalleryMark
import com.dublikunt.dmclient.data.repository.GalleryRepository
import com.dublikunt.dmclient.data.repository.HistoryRepository
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.data.repository.ReadingStatus
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.ui.components.userMessage
import com.dublikunt.dmclient.ui.navigation.GalleryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class GalleryUiState(
    val loading: Boolean = true,
    val detail: GalleryDetail? = null,
    val error: Throwable? = null,
    val mark: GalleryMark? = null,
    val statuses: List<ReadingStatus> = emptyList(),
    val lastPage: Int? = null,
    val download: DownloadItem? = null,
    val exporting: Boolean = false
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val galleries: GalleryRepository,
    private val library: LibraryRepository,
    history: HistoryRepository,
    private val downloads: DownloadRepository
) : ViewModel() {
    private val id = savedStateHandle.toRoute<GalleryRoute>().id
    private val detailState = MutableStateFlow(GalleryUiState())
    private val exporting = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var loadJob: Job? = null
    private var exportJob: Job? = null

    private val galleryState = combine(
        detailState, library.marks, library.statuses, history.lastPage(id), downloads.observe(id)
    ) { loaded, marks, statuses, lastPage, download ->
        loaded.copy(mark = marks[id], statuses = statuses, lastPage = lastPage, download = download)
    }
    val uiState: StateFlow<GalleryUiState> = combine(galleryState, exporting) { state, exporting ->
        state.copy(exporting = exporting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    init {
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            detailState.value = GalleryUiState()
            try {
                detailState.value = GalleryUiState(loading = false, detail = galleries.detail(id))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                detailState.value = GalleryUiState(loading = false, error = error)
            }
        }
    }

    fun thumbnail(detail: GalleryDetail, page: Int, downloaded: Boolean): Any =
        galleries.pageThumbnail(detail, page, downloaded)

    fun setFavorite(favorite: Boolean) = action {
        library.setFavorite(summary(), favorite)
    }

    fun setStatus(statusId: Int?) = action { library.setStatus(summary(), statusId) }

    fun createStatus(name: String, color: Int) = action {
        library.setStatus(summary(), library.createStatus(name, color))
    }

    fun enqueue() = action {
        val detail = detailState.value.detail ?: return@action
        downloads.enqueue(detail)
        messages.send("Download queued")
    }

    fun retryDownload() = action {
        downloads.retry(id)
        messages.send("Download queued")
    }

    fun cancelDownload() = action { downloads.cancel(id) }
    fun deleteDownload() = action { downloads.delete(id) }

    fun exportArchive() {
        if (exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            exporting.value = true
            try {
                downloads.exportArchive(id).collect { state ->
                    when (state) {
                        ArchiveState.Queued -> messages.send("Export queued")
                        ArchiveState.Running -> messages.send("Exporting ZIP…")
                        is ArchiveState.Completed -> messages.send("ZIP exported to Downloads")
                        is ArchiveState.Failed -> messages.send("Export failed: ${state.error}")
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send("Export failed: ${error.userMessage()}")
            } finally {
                exporting.value = false
            }
        }
    }

    private fun summary(): GallerySummary {
        val detail = checkNotNull(detailState.value.detail)
        return GallerySummary(detail.id, detail.title, detail.coverUrl)
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(error.userMessage())
            }
        }
    }
}
