package com.dublikunt.dmclient.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.data.repository.GalleryMark
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.network.GallerySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Extra row shown at the bottom of [GalleryActionsSheet]. */
data class GalleryAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@HiltViewModel
class GalleryActionsViewModel @Inject constructor(
    private val library: LibraryRepository
) : ViewModel() {
    val marks = library.marks
    val statuses = library.statuses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    fun setFavorite(gallery: GallerySummary, favorite: Boolean) {
        viewModelScope.launch {
            try {
                library.setFavorite(gallery, favorite)
                mutableError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableError.value = error.userMessage()
            }
        }
    }

    fun setStatus(gallery: GallerySummary, statusId: Int?) {
        viewModelScope.launch {
            try {
                library.setStatus(gallery, statusId)
                mutableError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableError.value = error.userMessage()
            }
        }
    }

    fun createStatus(gallery: GallerySummary, name: String, color: Int) {
        viewModelScope.launch {
            try {
                library.setStatus(gallery, library.createStatus(name, color))
                mutableError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableError.value = error.userMessage()
            }
        }
    }
}

/**
 * Long-press quick actions for a gallery: favorite toggle, reading status picker and
 * screen-specific [actions]. Library changes apply immediately.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun GalleryActionsSheet(
    gallery: GallerySummary,
    onDismiss: () -> Unit,
    actions: List<GalleryAction> = emptyList(),
    viewModel: GalleryActionsViewModel = hiltViewModel()
) {
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val mark = marks[gallery.id]
    var creatingStatus by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GalleryImage(
                    model = gallery.coverUrl,
                    contentDescription = null,
                    sizePx = 240,
                    modifier = Modifier
                        .width(56.dp)
                        .aspectRatio(GALLERY_COVER_ASPECT)
                        .clip(MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = gallery.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.size(16.dp))
            val favorite = mark?.favorite == true
            ToggleButton(
                checked = favorite,
                onCheckedChange = { viewModel.setFavorite(gallery, it) },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(if (favorite) "In favorites" else "Add to favorites")
            }
            Spacer(Modifier.size(20.dp))
            SectionHeader("Status")
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mark?.status == null,
                    onClick = { viewModel.setStatus(gallery, null) },
                    label = { Text("None") }
                )
                statuses.forEach { status ->
                    FilterChip(
                        selected = mark?.status?.id == status.id,
                        onClick = { viewModel.setStatus(gallery, status.id) },
                        label = { Text(status.name) },
                        leadingIcon = { StatusDot(Color(status.color), size = 10.dp) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { creatingStatus = true },
                    label = { Text("New") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                )
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (actions.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                actions.forEach { action ->
                    val tint =
                        if (action.destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    ListItem(
                        leadingContent = {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = tint
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                action.onClick()
                                onDismiss()
                            }
                    ) { Text(action.label, color = tint) }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    if (creatingStatus) {
        StatusEditorDialog(
            title = "New status",
            onSave = { name, color -> viewModel.createStatus(gallery, name, color) },
            onDismiss = { creatingStatus = false }
        )
    }
}

/** [GalleryCard] for a [GallerySummary] decorated with its library [mark]. */
@Composable
fun GallerySummaryCard(
    gallery: GallerySummary,
    mark: GalleryMark?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable () -> Unit)? = null
) {
    GalleryCard(
        title = gallery.title,
        cover = gallery.coverUrl,
        onClick = onClick,
        onLongClick = onLongClick,
        favorite = mark?.favorite == true,
        statusName = mark?.status?.name,
        statusColor = mark?.status?.color,
        overlay = overlay,
        modifier = modifier
    )
}
