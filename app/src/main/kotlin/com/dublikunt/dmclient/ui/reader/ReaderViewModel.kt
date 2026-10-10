package com.dublikunt.dmclient.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.repository.GalleryRepository
import com.dublikunt.dmclient.data.repository.HistoryRepository
import com.dublikunt.dmclient.data.settings.AppSettings
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.ui.components.userMessage
import com.dublikunt.dmclient.ui.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class ReaderUiState(
    val loading: Boolean = true,
    val detail: GalleryDetail? = null,
    val downloaded: Boolean = false,
    val error: Throwable? = null,
    val page: Int = 1,
    val settings: AppSettings = AppSettings()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val galleries: GalleryRepository,
    private val history: HistoryRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReaderRoute>()
    private val loaded = MutableStateFlow(ReaderUiState(settings = settings.settings.value))
    private val currentPage = MutableStateFlow<Int?>(null)
    private val progressMutex = Mutex()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var loadJob: Job? = null

    val uiState: StateFlow<ReaderUiState> = combine(loaded, currentPage, settings.settings) { state, page, settings ->
        state.copy(page = page ?: state.page, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), loaded.value)

    init {
        retry()
        viewModelScope.launch {
            currentPage.filterNotNull().debounce(500).collect { saveProgress() }
        }
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            loaded.value = ReaderUiState(settings = settings.settings.value)
            try {
                val lastPage = history.lastPage(route.id).first()
                val detail = galleries.detail(route.id)
                require(detail.pageCount > 0) { "This gallery has no pages." }
                val page = resolveStartPage(savedStateHandle["currentPage"] ?: route.page, lastPage, detail.pageCount)
                val downloaded = downloads.isDownloaded(route.id)
                loaded.value = ReaderUiState(loading = false, detail = detail, downloaded = downloaded, page = page)
                currentPage.value = page
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                loaded.value = ReaderUiState(loading = false, error = error)
            }
        }
    }

    fun pageImage(detail: GalleryDetail, page: Int, downloaded: Boolean): Any =
        galleries.pageImage(detail, page, downloaded)

    fun pageChanged(page: Int) {
        val detail = loaded.value.detail ?: return
        val safePage = page.coerceIn(1, detail.pageCount)
        savedStateHandle["currentPage"] = safePage
        currentPage.value = safePage
    }

    fun cycleMode() {
        viewModelScope.launch {
            try { settings.setReaderMode(nextReaderMode(settings.settings.value.readerMode)) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(error.userMessage())
            }
        }
    }

    fun flushProgress() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { saveProgress() }
        }
    }

    private suspend fun saveProgress() {
        if (loaded.value.detail == null) return
        progressMutex.withLock {
            val page = currentPage.value ?: return
            try { history.saveProgress(route.id, page) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.trySend("Could not save progress: ${error.userMessage()}")
            }
        }
    }
}
