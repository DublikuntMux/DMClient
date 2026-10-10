package com.dublikunt.dmclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.data.repository.ReadingStatus
import com.dublikunt.dmclient.ui.components.ConfirmDialog
import com.dublikunt.dmclient.ui.components.EmptyState
import com.dublikunt.dmclient.ui.components.LoadingState
import com.dublikunt.dmclient.ui.components.StatusDot
import com.dublikunt.dmclient.ui.components.StatusEditorDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusesScreen(onBack: () -> Unit) {
    val viewModel: StatusesViewModel = hiltViewModel()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReadingStatus?>(null) }
    var deleting by remember { mutableStateOf<ReadingStatus?>(null) }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Reading statuses") },
                scrollBehavior = scroll,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New status") },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        val current = statuses
        when {
            current == null -> LoadingState(modifier)
            current.isEmpty() ->
                EmptyState(
                    Icons.Rounded.Bookmarks,
                    "No reading statuses",
                    modifier,
                    "Create a status to organize your library.",
                )
            else ->
                LazyColumn(
                    modifier.padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(current, key = { _, status -> status.id }) { index, status ->
                        val shape =
                            when {
                                current.size == 1 -> MaterialTheme.shapes.extraLarge
                                index == 0 ->
                                    MaterialTheme.shapes.extraLarge.copy(
                                        bottomStart = MaterialTheme.shapes.extraSmall.bottomStart,
                                        bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd,
                                    )
                                index == current.lastIndex ->
                                    MaterialTheme.shapes.extraLarge.copy(
                                        topStart = MaterialTheme.shapes.extraSmall.topStart,
                                        topEnd = MaterialTheme.shapes.extraSmall.topEnd,
                                    )
                                else -> MaterialTheme.shapes.extraSmall
                            }
                        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { editing = status }
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusDot(Color(status.color), size = 16.dp)
                                Text(
                                    status.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = { viewModel.move(status.id, -1) },
                                    enabled = index > 0,
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, "Move ${status.name} up")
                                }
                                IconButton(
                                    onClick = { viewModel.move(status.id, 1) },
                                    enabled = index < current.lastIndex,
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        "Move ${status.name} down",
                                    )
                                }
                                IconButton(onClick = { deleting = status }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        "Delete ${status.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
    if (creating) StatusEditorDialog("New status", viewModel::create, { creating = false })
    editing?.let { status ->
        StatusEditorDialog(
            "Edit status",
            { name, color -> viewModel.update(status.copy(name = name, color = color)) },
            { editing = null },
            status.name,
            status.color,
        )
    }
    deleting?.let { status ->
        ConfirmDialog(
            "Delete ${status.name}?",
            "Galleries with this status keep their favorites, but will no longer have this reading status.",
            "Delete",
            { viewModel.delete(status.id) },
            { deleting = null },
            destructive = true,
        )
    }
}
