package com.dublikunt.dmclient.ui.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.data.repository.DownloadItem
import com.dublikunt.dmclient.data.repository.DownloadState
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.ui.components.ConfirmDialog
import com.dublikunt.dmclient.ui.components.EmptyState
import com.dublikunt.dmclient.ui.components.GalleryAction
import com.dublikunt.dmclient.ui.components.GalleryActionsSheet
import com.dublikunt.dmclient.ui.components.GalleryGridDefaults
import com.dublikunt.dmclient.ui.components.GalleryImage
import com.dublikunt.dmclient.ui.components.GallerySummaryCard
import com.dublikunt.dmclient.ui.components.LoadingState
import com.dublikunt.dmclient.ui.components.SectionHeader
import com.dublikunt.dmclient.ui.components.fullWidthItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(onOpenGallery: (Int) -> Unit) {
    val viewModel: DownloadsViewModel = hiltViewModel()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bytes by viewModel.downloadsBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var overflow by remember { mutableStateOf(false) }
    var deleteAll by rememberSaveable { mutableStateOf(false) }
    var actionGallery by remember { mutableStateOf<GallerySummary?>(null) }
    var deleteGallery by remember { mutableStateOf<GallerySummary?>(null) }
    var cancelGallery by remember { mutableStateOf<GallerySummary?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<Int?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingExport?.let {
                if (granted) viewModel.export(it) else viewModel.permissionDenied()
            }
            pendingExport = null
        }
    fun export(id: Int) {
        if (
            Build.VERSION.SDK_INT == 28 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingExport = id
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else viewModel.export(id)
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                scrollBehavior = scroll,
                actions = {
                    Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Rounded.MoreVert, "More options")
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete all downloads") },
                                onClick = {
                                    overflow = false
                                    deleteAll = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        val items = downloads
        when {
            items == null -> LoadingState(modifier)
            items.isEmpty() ->
                EmptyState(
                    Icons.Rounded.FileDownload,
                    "No downloads",
                    modifier,
                    "Downloaded galleries are available offline.",
                )
            else -> {
                val active = items.filter {
                    it.state == DownloadState.Queued || it.state == DownloadState.Downloading
                }
                val failed = items.filter { it.state == DownloadState.Failed }
                val completed = items.filter { it.state == DownloadState.Completed }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(settings.gridDensity.minCell.dp),
                    modifier = modifier,
                    contentPadding = GalleryGridDefaults.ContentPadding,
                    horizontalArrangement = GalleryGridDefaults.HorizontalSpacing,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (active.isNotEmpty()) {
                        fullWidthItem("active") {
                            SectionHeader("In progress", Modifier.padding(vertical = 8.dp))
                        }
                        active.forEach { item ->
                            fullWidthItem(item.gallery.id) {
                                DownloadProgressCard(
                                    item,
                                    onCancel = { cancelGallery = item.gallery },
                                    onRetry = { viewModel.retry(item.gallery.id) },
                                    onDelete = { deleteGallery = item.gallery },
                                )
                            }
                        }
                    }
                    if (failed.isNotEmpty()) {
                        fullWidthItem("failed") {
                            SectionHeader("Failed", Modifier.padding(vertical = 8.dp))
                        }
                        failed.forEach { item ->
                            fullWidthItem(item.gallery.id) {
                                DownloadProgressCard(
                                    item,
                                    onCancel = { cancelGallery = item.gallery },
                                    onRetry = { viewModel.retry(item.gallery.id) },
                                    onDelete = { deleteGallery = item.gallery },
                                )
                            }
                        }
                    }
                    if (completed.isNotEmpty()) {
                        fullWidthItem("completed") {
                            SectionHeader(
                                "Downloaded · ${completed.size}",
                                Modifier.padding(vertical = 8.dp),
                                action = {
                                    Text(
                                        bytes?.let { Formatter.formatFileSize(context, it) }
                                            ?: "Calculating…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                        items(completed, key = { it.gallery.id }) { item ->
                            GallerySummaryCard(
                                item.gallery,
                                marks[item.gallery.id],
                                { onOpenGallery(item.gallery.id) },
                                { actionGallery = item.gallery },
                            )
                        }
                    }
                }
            }
        }
    }
    actionGallery?.let { gallery ->
        GalleryActionsSheet(
            gallery,
            { actionGallery = null },
            actions =
                listOf(
                    GalleryAction("Export as ZIP", Icons.Rounded.FolderZip) { export(gallery.id) },
                    GalleryAction("Delete download", Icons.Rounded.Delete, destructive = true) {
                        deleteGallery = gallery
                    },
                ),
        )
    }
    cancelGallery?.let { gallery ->
        ConfirmDialog(
            "Cancel download?",
            gallery.title,
            "Cancel download",
            { viewModel.cancel(gallery.id) },
            { cancelGallery = null },
            destructive = true,
        )
    }
    deleteGallery?.let { gallery ->
        ConfirmDialog(
            "Delete download?",
            "The downloaded files for ${gallery.title} will be removed.",
            "Delete",
            { viewModel.delete(gallery.id) },
            { deleteGallery = null },
            destructive = true,
        )
    }
    if (deleteAll)
        ConfirmDialog(
            "Delete all downloads?",
            "All downloaded files and downloads in progress will be removed.",
            "Delete all",
            viewModel::deleteAll,
            { deleteAll = false },
            destructive = true,
        )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadProgressCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GalleryImage(
                item.gallery.coverUrl,
                null,
                Modifier.size(48.dp, 68.dp).clip(MaterialTheme.shapes.medium),
                sizePx = 240,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.gallery.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.state == DownloadState.Failed) {
                    Text(
                        item.error ?: "Download failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    LinearWavyProgressIndicator(
                        progress = {
                            if (item.pageCount > 0)
                                (item.downloadedPages.toFloat() / item.pageCount).coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (item.state == DownloadState.Queued) "Waiting for network…"
                        else "${item.downloadedPages} / ${item.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.state == DownloadState.Failed) {
                Column {
                    IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Retry download") }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            "Delete download",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel download") }
        }
    }
}
