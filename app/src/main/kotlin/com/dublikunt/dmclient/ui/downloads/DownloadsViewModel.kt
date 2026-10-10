package com.dublikunt.dmclient.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.data.repository.ArchiveState
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.data.repository.StorageRepository
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val repository: DownloadRepository,
    private val storage: StorageRepository,
    library: LibraryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val downloads =
        repository.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val marks = library.marks
    val settings = settingsRepository.settings
    private val mutableBytes = MutableStateFlow<Long?>(null)
    val downloadsBytes = mutableBytes.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val exportingIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.downloads.collect {
                try {
                    mutableBytes.value = storage.usage().downloadsBytes
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    messages.send(error.userMessage())
                }
            }
        }
    }

    fun cancel(id: Int) = action("Download canceled") { repository.cancel(id) }

    fun delete(id: Int) = action("Download deleted") { repository.delete(id) }

    fun deleteAll() = action("All downloads deleted") { repository.deleteAll() }

    fun retry(id: Int) = action("Download queued") { repository.retry(id) }

    fun permissionDenied() {
        messages.trySend("Storage permission is needed to export a ZIP.")
    }

    fun export(id: Int) {
        if (!synchronized(exportingIds) { exportingIds.add(id) }) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                messages.send("Exporting ZIP…")
                repository.exportArchive(id).collect { state ->
                    when (state) {
                        is ArchiveState.Completed -> messages.send("ZIP saved to Downloads")
                        is ArchiveState.Failed -> messages.send("Export failed: ${state.error}")
                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.send(error.userMessage())
            } finally {
                synchronized(exportingIds) { exportingIds.remove(id) }
            }
        }
    }

    private fun action(message: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
                messages.send(message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.send(error.userMessage())
            }
        }
    }
}
