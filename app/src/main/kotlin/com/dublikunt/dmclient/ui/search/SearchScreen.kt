package com.dublikunt.dmclient.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.ui.components.EmptyState
import com.dublikunt.dmclient.ui.components.GalleryAction
import com.dublikunt.dmclient.ui.components.GalleryActionsSheet
import com.dublikunt.dmclient.ui.components.GallerySummaryCard
import com.dublikunt.dmclient.ui.components.PagingGalleryGrid
import com.dublikunt.dmclient.ui.components.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(onOpenGallery: (Int) -> Unit) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val dataStatus by viewModel.searchDataStatus.collectAsStateWithLifecycle()
    val galleries = viewModel.results.collectAsLazyPagingItems()
    val searchBarState = rememberSearchBarState()
    val textFieldState = remember { TextFieldState(state.query) }
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedGallery by remember { mutableStateOf<GallerySummary?>(null) }

    LaunchedEffect(textFieldState, viewModel) {
        snapshotFlow { textFieldState.text.toString() }.collect(viewModel::setQuery)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    val addFilter: (Tag, Boolean) -> Unit = { tag, excluded ->
        textFieldState.setTextAndPlaceCursorAtEnd("")
        viewModel.addFilter(tag, excluded)
        scope.launch { searchBarState.animateToCollapsed() }
    }
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { query ->
                viewModel.submit(query)
                textFieldState.setTextAndPlaceCursorAtEnd(query.trim())
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = { Text("Search galleries or tags") },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.setTextAndPlaceCursorAtEnd("")
                        viewModel.setQuery("")
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear input")
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                inputField = inputField,
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            SearchFilters(
                state = state,
                onSort = viewModel::setSort,
                onLanguage = viewModel::setLanguage,
                onToggleFilter = viewModel::toggleFilter,
                onRemoveFilter = viewModel::removeFilter,
                onEditText = {
                    textFieldState.setTextAndPlaceCursorAtEnd(state.text)
                    viewModel.setQuery(state.text)
                    scope.launch { searchBarState.animateToExpanded() }
                },
                onRemoveText = {
                    textFieldState.setTextAndPlaceCursorAtEnd("")
                    viewModel.removeText()
                }
            )
            if (state.request() == null) {
                SearchIdleState(dataStatus, viewModel::refreshSearchData, Modifier.weight(1f))
            } else {
                PagingGalleryGrid(
                    items = galleries,
                    minCellSize = settings.gridDensity.minCell.dp,
                    modifier = Modifier.weight(1f),
                    errorMessage = { it.userMessage() },
                    emptyContent = {
                        EmptyState(
                            Icons.Rounded.Search,
                            "No galleries found",
                            message = "Try another title or change your filters."
                        )
                    }
                ) { gallery ->
                    GallerySummaryCard(
                        gallery = gallery,
                        mark = marks[gallery.id],
                        onClick = { onOpenGallery(gallery.id) },
                        onLongClick = { selectedGallery = gallery }
                    )
                }
            }
        }
    }

    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        SearchSuggestions(
            query = state.query,
            suggestions = suggestions,
            onInclude = { addFilter(it, false) },
            onExclude = { addFilter(it, true) }
        )
    }

    selectedGallery?.let { gallery ->
        GalleryActionsSheet(
            gallery = gallery,
            onDismiss = { selectedGallery = null },
            actions = listOf(
                GalleryAction("Download", Icons.Rounded.FileDownload) { viewModel.download(gallery) }
            )
        )
    }
}
