package com.dublikunt.dmclient.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.dublikunt.dmclient.data.repository.HistoryItem
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.ui.components.ConfirmDialog
import com.dublikunt.dmclient.ui.components.EmptyState
import com.dublikunt.dmclient.ui.components.ErrorState
import com.dublikunt.dmclient.ui.components.GalleryAction
import com.dublikunt.dmclient.ui.components.GalleryActionsSheet
import com.dublikunt.dmclient.ui.components.GalleryGrid
import com.dublikunt.dmclient.ui.components.GalleryImage
import com.dublikunt.dmclient.ui.components.GallerySummaryCard
import com.dublikunt.dmclient.ui.components.InlineError
import com.dublikunt.dmclient.ui.components.LoadingState
import com.dublikunt.dmclient.ui.components.StatusDot
import com.dublikunt.dmclient.ui.components.userMessage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onOpenGallery: (Int) -> Unit,
    onContinue: (Int) -> Unit,
    onManageStatuses: () -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val entryState by viewModel.entries.collectAsStateWithLifecycle()
    val entries = entryState.items
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var actionGallery by remember { mutableStateOf<GallerySummary?>(null) }
    var historyAction by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var clearHistory by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    BackHandler(searching) { viewModel.setSearching(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scroll,
                navigationIcon = {
                    if (searching)
                        IconButton(onClick = { viewModel.setSearching(false) }) {
                            Icon(Icons.Rounded.Close, "Close search")
                        }
                },
                title = {
                    if (searching)
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::search,
                            singleLine = true,
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                            decorationBox = { field ->
                                Box {
                                    if (query.isEmpty())
                                        Text(
                                            "Search library",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    field()
                                }
                            },
                        )
                    else Text("Library")
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searching) viewModel.search("") else viewModel.setSearching(true)
                        }
                    ) {
                        Icon(
                            if (searching) Icons.Rounded.Clear else Icons.Rounded.Search,
                            if (searching) "Clear search" else "Search library",
                        )
                    }
                    if (selection == "history") {
                        Box {
                            IconButton(onClick = { overflow = true }) {
                                Icon(Icons.Rounded.MoreVert, "More options")
                            }
                            DropdownMenu(
                                expanded = overflow,
                                onDismissRequest = { overflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear history") },
                                    onClick = {
                                        overflow = false
                                        clearHistory = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selection == "history",
                        onClick = { viewModel.select("history") },
                        label = { Text("History") },
                        leadingIcon = { Icon(Icons.Rounded.History, null) },
                    )
                }
                item {
                    FilterChip(
                        selected = selection == "favorites",
                        onClick = { viewModel.select("favorites") },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                if (selection == "favorites") Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                null
                            )
                        },
                    )
                }
                item {
                    FilterChip(
                        selected = selection == "all",
                        onClick = { viewModel.select("all") },
                        label = { Text("All tracked") },
                    )
                }
                items(statuses, key = { it.id }) { status ->
                    FilterChip(
                        selected = selection == status.id.toString(),
                        onClick = { viewModel.select(status.id.toString()) },
                        label = { Text(status.name) },
                        leadingIcon = { StatusDot(Color(status.color)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onManageStatuses,
                        label = { Text("Manage") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                    )
                }
            }
            when {
                selection == null -> LoadingState()
                selection == "history" -> {
                    when (val refresh = history.loadState.refresh) {
                        is LoadState.Loading -> if (history.itemCount == 0) LoadingState()
                        is LoadState.Error ->
                            if (history.itemCount == 0)
                                ErrorState(refresh.error.userMessage(), onRetry = history::retry)

                        else -> Unit
                    }
                    if (
                        history.itemCount == 0 && history.loadState.refresh is LoadState.NotLoading
                    ) {
                        EmptyState(
                            Icons.Rounded.History,
                            if (query.isEmpty()) "No history yet" else "No matches",
                            message =
                                if (query.isEmpty()) "Galleries you open appear here."
                                else "Try another search.",
                        )
                    } else if (history.itemCount > 0) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            items(
                                history.itemCount,
                                key = history.itemKey { it.gallery.id }) { index ->
                                history[index]?.let { entry ->
                                    val dismiss = rememberSwipeToDismissBoxState()
                                    LaunchedEffect(dismiss.currentValue) {
                                        if (
                                            dismiss.currentValue ==
                                            SwipeToDismissBoxValue.EndToStart
                                        )
                                            viewModel.removeHistory(entry.gallery.id)
                                    }
                                    SwipeToDismissBox(
                                        state = dismiss,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        MaterialTheme.colorScheme.errorContainer
                                                    )
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.CenterEnd,
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    "Remove from history",
                                                    tint =
                                                        MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                            }
                                        },
                                    ) {
                                        HistoryRow(
                                            entry,
                                            { onOpenGallery(entry.gallery.id) },
                                            { onContinue(entry.gallery.id) },
                                            {
                                                historyAction = true
                                                actionGallery = entry.gallery
                                            },
                                        )
                                    }
                                }
                            }
                            when (val append = history.loadState.append) {
                                is LoadState.Loading ->
                                    item {
                                        LoadingIndicator(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        )
                                    }

                                is LoadState.Error ->
                                    item { InlineError(append.error.userMessage(), history::retry) }

                                else -> Unit
                            }
                        }
                    }
                }

                entryState.loading -> LoadingState()
                entryState.error != null ->
                    ErrorState(entryState.error!!, onRetry = viewModel::retryEntries)

                entries.isEmpty() ->
                    EmptyState(
                        icon =
                            if (selection == "favorites") Icons.Rounded.Favorite
                            else Icons.Rounded.CollectionsBookmark,
                        title =
                            if (query.isNotEmpty()) "No matches"
                            else if (selection == "favorites") "No favorites yet"
                            else "No galleries yet",
                        message =
                            if (query.isNotEmpty()) "Try another search."
                            else if (selection == "favorites")
                                "Tap the heart on a gallery to save it here."
                            else "Assign a reading status to a gallery to track it.",
                    )

                else ->
                    GalleryGrid(
                        entries,
                        settings.gridDensity.minCell.dp,
                        key = { it.gallery.id },
                    ) { entry ->
                        GallerySummaryCard(
                            entry.gallery,
                            marks[entry.gallery.id],
                            { onOpenGallery(entry.gallery.id) },
                            {
                                historyAction = false
                                actionGallery = entry.gallery
                            },
                        )
                    }
            }
        }
    }
    actionGallery?.let { gallery ->
        GalleryActionsSheet(
            gallery,
            { actionGallery = null },
            actions =
                if (historyAction)
                    listOf(
                        GalleryAction(
                            "Remove from history",
                            Icons.Rounded.Delete,
                            destructive = true,
                        ) {
                            viewModel.removeHistory(gallery.id)
                        }
                    )
                else emptyList(),
        )
    }
    if (clearHistory)
        ConfirmDialog(
            "Clear history?",
            "All reading history and saved page positions will be removed.",
            "Clear history",
            viewModel::clearHistory,
            { clearHistory = false },
            destructive = true,
        )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryItem,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onLongClick: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.openedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GalleryImage(
                entry.gallery.coverUrl,
                null,
                Modifier
                    .size(56.dp, 80.dp)
                    .clip(MaterialTheme.shapes.medium),
                sizePx = 240,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    entry.gallery.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Page ${entry.lastPage} of ${entry.pageCount}",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = {
                        if (entry.pageCount > 0)
                            (entry.lastPage.toFloat() / entry.pageCount).coerceIn(0f, 1f)
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
                Text(
                    relativeTime(entry.openedAt, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onContinue, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.PlayArrow, "Continue")
            }
        }
    }
}
