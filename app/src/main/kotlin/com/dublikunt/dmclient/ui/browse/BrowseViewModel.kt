package com.dublikunt.dmclient.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.repository.GalleryRepository
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BrowseViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val galleries: GalleryRepository,
    library: LibraryRepository,
    private val downloads: DownloadRepository
) : ViewModel() {
    val settings = settingsRepository.settings
    val marks = library.marks
    val galleriesFlow = settings.map { it.language }
        .distinctUntilChanged()
        .flatMapLatest(galleries::latest)
        .cachedIn(viewModelScope)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun setLanguage(language: ContentLanguage) {
        viewModelScope.launch {
            try {
                settingsRepository.setLanguage(language)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(error.userMessage())
            }
        }
    }

    fun download(gallery: GallerySummary) {
        viewModelScope.launch {
            try {
                downloads.enqueue(galleries.detail(gallery.id))
                messages.send("Download queued")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(error.userMessage())
            }
        }
    }
}
