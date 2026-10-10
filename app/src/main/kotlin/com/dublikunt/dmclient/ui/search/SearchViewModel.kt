package com.dublikunt.dmclient.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.repository.GalleryRepository
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.data.repository.SearchDataRepository
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.network.SearchRequest
import com.dublikunt.dmclient.network.SortOrder
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.ui.components.userMessage
import com.dublikunt.dmclient.ui.navigation.SearchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SearchFilter(val tag: Tag, val excluded: Boolean = false)

data class SearchState(
    val text: String = "",
    val query: String = "",
    val filters: List<SearchFilter> = emptyList(),
    val sort: SortOrder = SortOrder.Recent,
    val language: ContentLanguage = ContentLanguage.All
) {
    fun addFilter(tag: Tag, excluded: Boolean = false): SearchState {
        val existing = filters.indexOfFirst { it.tag.type == tag.type && it.tag.name == tag.name }
        val filter = SearchFilter(tag, excluded)
        val updated = if (existing < 0) filters + filter else filters.toMutableList().apply {
            this[existing] = filter
        }.toList()
        return copy(query = "", filters = updated)
    }

    fun toggleFilter(tag: Tag): SearchState = copy(filters = filters.map {
        if (it.tag.type == tag.type && it.tag.name == tag.name) it.copy(excluded = !it.excluded)
        else it
    })

    fun removeFilter(tag: Tag): SearchState = copy(filters = filters.filterNot {
        it.tag.type == tag.type && it.tag.name == tag.name
    })

    fun submit(query: String): SearchState = copy(text = query.trim(), query = query.trim())

    fun removeText(): SearchState = copy(text = "", query = "")

    fun request(): SearchRequest? {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty() && filters.isEmpty()) return null
        return SearchRequest(
            text = normalizedText,
            include = filters.filterNot { it.excluded }.map { it.tag },
            exclude = filters.filter { it.excluded }.map { it.tag },
            sort = sort,
            language = language
        )
    }

    companion object {
        fun fromRoute(route: SearchRoute, language: ContentLanguage): SearchState {
            val state = SearchState(language = language).submit(route.text)
            return if (route.tagType != null && !route.tagName.isNullOrBlank()) {
                state.addFilter(Tag(route.tagType, route.tagName)).copy(query = state.text)
            } else state
        }
    }
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val galleries: GalleryRepository,
    library: LibraryRepository,
    private val searchData: SearchDataRepository,
    private val downloads: DownloadRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SearchState.fromRoute(savedStateHandle.toRoute<SearchRoute>(), settingsRepository.settings.value.language)
    )
    val state = mutableState.asStateFlow()
    val settings = settingsRepository.settings
    val marks = library.marks
    val searchDataStatus = searchData.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var languageSelected = false
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    val suggestions = combine(
        state.map { it.query }.distinctUntilChanged().debounce(150),
        searchDataStatus.map { it?.counts }.distinctUntilChanged()
    ) { query, _ -> query }
        .mapLatest { query ->
            try {
                withContext(Dispatchers.IO) { searchData.suggest(query, 30) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(error.userMessage())
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val results = state.map { it.request() }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            if (request == null) flowOf(PagingData.empty()) else galleries.search(request)
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            settings.map { it.language }.distinctUntilChanged().collect { language ->
                if (!languageSelected) mutableState.update { it.copy(language = language) }
            }
        }
        viewModelScope.launch {
            try {
                searchData.ensureSeeded()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(error.userMessage())
            }
        }
    }

    fun setQuery(query: String) { mutableState.update { it.copy(query = query) } }
    fun submit(query: String) { mutableState.update { it.submit(query) } }
    fun addFilter(tag: Tag, excluded: Boolean = false) {
        mutableState.update { it.addFilter(tag, excluded) }
    }
    fun toggleFilter(tag: Tag) { mutableState.update { it.toggleFilter(tag) } }
    fun removeFilter(tag: Tag) { mutableState.update { it.removeFilter(tag) } }
    fun removeText() { mutableState.update { it.removeText() } }
    fun setSort(sort: SortOrder) { mutableState.update { it.copy(sort = sort) } }
    fun setLanguage(language: ContentLanguage) {
        languageSelected = true
        mutableState.update { it.copy(language = language) }
    }
    fun refreshSearchData() { searchData.refresh() }

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
