package com.dublikunt.dmclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.repository.HistoryRepository
import com.dublikunt.dmclient.data.repository.SearchDataRepository
import com.dublikunt.dmclient.data.repository.StorageRepository
import com.dublikunt.dmclient.data.repository.StorageUsage
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StorageViewModel
@Inject
constructor(
    private val storage: StorageRepository,
    private val history: HistoryRepository,
    private val downloads: DownloadRepository,
    private val searchData: SearchDataRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings
    private val mutableUsage = MutableStateFlow<StorageUsage?>(null)
    val usage = mutableUsage.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutableUsage.value = storage.usage()
                mutableError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableError.value = error.userMessage()
            }
        }
    }

    fun setCacheSize(bytes: Long) =
        action("Cache size saved. Applies after restart.") {
            settingsRepository.setImageCacheSize(bytes)
        }

    fun clearCache() = action("Image cache cleared") { storage.clearImageCache() }

    fun clearHistory() = action("History cleared") { history.clear() }

    fun deleteDownloads() = action("All downloads deleted") { downloads.deleteAll() }

    fun clearTags() = action("Tag list cleared") { searchData.clear() }

    private fun action(message: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
                mutableUsage.value = storage.usage()
                mutableError.value = null
                messages.send(message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.send(error.userMessage())
            }
        }
    }
}
