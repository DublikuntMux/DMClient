package com.dublikunt.dmclient.ui.gallery

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dublikunt.dmclient.data.repository.DownloadState
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.network.TagType
import com.dublikunt.dmclient.ui.components.ConfirmDialog
import com.dublikunt.dmclient.ui.components.GALLERY_COVER_ASPECT
import com.dublikunt.dmclient.ui.components.GalleryGridDefaults
import com.dublikunt.dmclient.ui.components.GalleryImage
import com.dublikunt.dmclient.ui.components.SectionHeader
import com.dublikunt.dmclient.ui.components.StatusDot
import com.dublikunt.dmclient.ui.components.StatusEditorDialog
import com.dublikunt.dmclient.ui.components.TagChip
import com.dublikunt.dmclient.ui.components.fullWidthItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryContent(
    state: GalleryUiState,
    grid: LazyGridState,
    headerTopPadding: Dp,
    contentPadding: PaddingValues,
    viewModel: GalleryViewModel,
    onRead: (Int, Int?) -> Unit,
    onSearchTag: (Tag) -> Unit,
    onCopyTag: (Tag) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = checkNotNull(state.detail)
    val downloaded = state.download?.state == DownloadState.Completed
    val tagGroups = listOf(
        TagType.Parody to "Parodies", TagType.Character to "Characters", TagType.Tag to "Tags",
        TagType.Artist to "Artists", TagType.Group to "Groups", TagType.Language to "Languages",
        TagType.Category to "Categories"
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp), state = grid, modifier = modifier,
        contentPadding = PaddingValues(
            start = GalleryGridDefaults.ContentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = GalleryGridDefaults.ContentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = contentPadding.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fullWidthItem("header") {
            GalleryHeader(detail, state.download?.gallery?.coverUrl ?: detail.coverUrl, headerTopPadding)
        }
        fullWidthItem("actions") {
            GalleryActionRow(state, viewModel, onRead, onExport)
        }
        tagGroups.forEach { (type, label) ->
            val tags = detail.tags.filter { it.type == type }
            if (tags.isNotEmpty()) fullWidthItem(type) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            TagChip(label = tag.name, count = tag.count, onClick = { onSearchTag(tag) }, onLongClick = { onCopyTag(tag) })
                        }
                    }
                }
            }
        }
        fullWidthItem("pages") {
            SectionHeader("Pages", Modifier.padding(horizontal = 4.dp, vertical = 12.dp)) {
                Text(detail.pageCount.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(detail.pageCount, key = { "page_${it + 1}" }) { index ->
            val page = index + 1
            Box(
                Modifier.fillMaxWidth().aspectRatio(GALLERY_COVER_ASPECT)
                    .clip(MaterialTheme.shapes.medium)
                    .then(if (state.lastPage == page) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .clickable { onRead(detail.id, page) }
            ) {
                GalleryImage(
                    model = viewModel.thumbnail(detail, page, downloaded), contentDescription = "Page $page",
                    sizePx = 360, modifier = Modifier.fillMaxSize().padding(if (state.lastPage == page) 2.dp else 0.dp)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                ) { Text(page.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
            }
        }
    }
}

@Composable
private fun GalleryHeader(detail: GalleryDetail, cover: Any, topPadding: Dp) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().heightIn(min = 260.dp + topPadding)) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            GalleryImage(
                model = cover, contentDescription = null, modifier = Modifier.matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier)
            )
            Box(Modifier.matchParentSize().background(surface.copy(alpha = 0.55f)))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.1f), surface))))
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = topPadding + 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom
        ) {
            GalleryImage(
                model = cover, contentDescription = "Cover", sizePx = 480,
                modifier = Modifier.width(120.dp).aspectRatio(GALLERY_COVER_ASPECT)
                    .shadow(4.dp, MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.title, style = MaterialTheme.typography.titleLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                detail.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GalleryMeta(Icons.Rounded.PhotoLibrary, "${detail.pageCount} pages", Modifier.weight(1f))
                    GalleryMeta(Icons.Rounded.Numbers, "${detail.id}")
                }
                detail.uploadDate?.let {
                    GalleryMeta(Icons.Rounded.CalendarToday, formatUploadDate(it, System.currentTimeMillis() / 1_000))
                }
                detail.favorites?.let { GalleryMeta(Icons.Rounded.FavoriteBorder, "$it favorites") }
            }
        }
    }
}

@Composable
private fun GalleryMeta(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GalleryActionRow(state: GalleryUiState, viewModel: GalleryViewModel, onRead: (Int, Int?) -> Unit, onExport: () -> Unit) {
    val detail = checkNotNull(state.detail)
    var statusMenu by remember { mutableStateOf(false) }
    var newStatus by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        val continuing = (state.lastPage ?: 1) > 1
        Button(
            onClick = { onRead(detail.id, if (continuing) null else 1) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
            Text(if (continuing) "Continue · p. ${state.lastPage}" else "Read", maxLines = 2)
        }
        FilledTonalIconToggleButton(
            checked = state.mark?.favorite == true, onCheckedChange = viewModel::setFavorite,
            shapes = IconButtonDefaults.toggleableShapes(), modifier = Modifier.size(48.dp)
        ) {
            Icon(if (state.mark?.favorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite")
        }
        Box {
            FilledTonalButton(
                onClick = { statusMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.widthIn(max = 96.dp).heightIn(min = 48.dp)
            ) {
                state.mark?.status?.let {
                    StatusDot(Color(it.color))
                    Spacer(Modifier.size(4.dp))
                }
                Text(state.mark?.status?.name ?: "Status", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                DropdownMenuItem(
                    text = { Text("None") }, trailingIcon = { if (state.mark?.status == null) Icon(Icons.Rounded.Check, null) },
                    onClick = { statusMenu = false; viewModel.setStatus(null) }
                )
                state.statuses.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status.name) }, leadingIcon = { StatusDot(Color(status.color)) },
                        trailingIcon = { if (state.mark?.status?.id == status.id) Icon(Icons.Rounded.Check, null) },
                        onClick = { statusMenu = false; viewModel.setStatus(status.id) }
                    )
                }
                DropdownMenuItem(text = { Text("New status…") }, onClick = { statusMenu = false; newStatus = true })
            }
        }
        GalleryDownloadControl(state, viewModel, onExport)
    }
    if (newStatus) StatusEditorDialog(title = "New status", onSave = viewModel::createStatus, onDismiss = { newStatus = false })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GalleryDownloadControl(state: GalleryUiState, viewModel: GalleryViewModel, onExport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val downloading = state.download?.state in listOf(DownloadState.Queued, DownloadState.Downloading)
    val failed = state.download?.state == DownloadState.Failed
    Box {
        FilledTonalIconButton(
            onClick = {
                when (state.download?.state) {
                    null -> viewModel.enqueue()
                    DownloadState.Queued, DownloadState.Downloading -> cancel = true
                    DownloadState.Failed -> viewModel.retryDownload()
                    DownloadState.Completed -> menu = true
                }
            }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                contentColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            if (downloading) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { ((state.download?.downloadedPages ?: 0).toFloat() / (state.download?.pageCount ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.size(36.dp)
                    )
                    Icon(Icons.Rounded.Stop, "Cancel download", Modifier.size(16.dp))
                }
            } else Icon(
                when (state.download?.state) {
                    DownloadState.Completed -> Icons.Rounded.DownloadDone
                    DownloadState.Failed -> Icons.Rounded.Refresh
                    else -> Icons.Rounded.Download
                }, when {
                    failed -> "Retry download"
                    state.download?.state == DownloadState.Completed -> "Download options"
                    else -> "Download gallery"
                }
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (state.exporting) "Exporting ZIP…" else "Export as ZIP") }, enabled = !state.exporting, onClick = { menu = false; onExport() })
            DropdownMenuItem(text = { Text("Delete download", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete = true })
        }
    }
    if (cancel) ConfirmDialog(
        title = "Cancel download?", message = "Downloaded pages will be removed.", confirmLabel = "Cancel download",
        destructive = true, onConfirm = viewModel::cancelDownload, onDismiss = { cancel = false }
    )
    if (delete) ConfirmDialog(
        title = "Delete download?", message = "Remove this gallery's downloaded pages from your device.", confirmLabel = "Delete",
        destructive = true, onConfirm = viewModel::deleteDownload, onDismiss = { delete = false }
    )
}
