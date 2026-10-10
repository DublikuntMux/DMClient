package com.dublikunt.dmclient.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.dublikunt.dmclient.data.repository.HistoryRepository
import com.dublikunt.dmclient.data.repository.LibraryFilter
import com.dublikunt.dmclient.data.repository.LibraryItem
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryEntriesState(
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val library: LibraryRepository,
    private val historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val selection = savedState.getStateFlow<String?>("filter", null)
    val query = savedState.getStateFlow("query", "")
    val searching = savedState.getStateFlow("searching", false)
    val settings = settingsRepository.settings
    val marks = library.marks
    val statuses =
        library.statuses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val reload = MutableStateFlow(0)
    val entries =
        combine(selection, query, reload) { filter, text, _ -> filter to text }
            .flatMapLatest { (filter, text) ->
                val source =
                    when (filter) {
                        "favorites" -> library.entries(LibraryFilter.Favorites, text)
                        "all" -> library.entries(LibraryFilter.AllTracked, text)
                        null,
                        "history" -> flowOf(emptyList())

                        else -> library.entries(LibraryFilter.Status(filter.toInt()), text)
                    }
                source
                    .map { LibraryEntriesState(items = it, loading = false) }
                    .onStart { emit(LibraryEntriesState()) }
                    .catch { error ->
                        emit(LibraryEntriesState(loading = false, error = error.userMessage()))
                    }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryEntriesState())
    val history = query.flatMapLatest(historyRepository::history).cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            if (selection.value == null) {
                try {
                    val favorites =
                        library.entries(LibraryFilter.Favorites, "").flowOn(Dispatchers.IO).first()
                    if (selection.value == null)
                        select(if (favorites.isEmpty()) "history" else "favorites")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (selection.value == null) select("history")
                    messages.send(error.userMessage())
                }
            }
        }
        viewModelScope.launch {
            library.statuses.collect { statuses ->
                selection.value?.toIntOrNull()?.let { id ->
                    if (statuses.none { it.id == id }) select("all")
                }
            }
        }
    }

    fun select(value: String) {
        savedState["filter"] = value
    }

    fun retryEntries() {
        reload.value += 1
    }

    fun search(value: String) {
        savedState["query"] = value
    }

    fun setSearching(value: Boolean) {
        savedState["searching"] = value
        if (!value) search("")
    }

    fun removeHistory(id: Int) = action("Removed from history") { historyRepository.remove(id) }

    fun clearHistory() = action("History cleared") { historyRepository.clear() }

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
