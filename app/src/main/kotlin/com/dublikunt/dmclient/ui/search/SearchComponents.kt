package com.dublikunt.dmclient.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dublikunt.dmclient.data.repository.SearchDataStatus
import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.network.SortOrder
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.network.TagType
import com.dublikunt.dmclient.ui.components.EmptyState
import java.text.NumberFormat

@Composable
internal fun SearchFilters(
    state: SearchState,
    onSort: (SortOrder) -> Unit,
    onLanguage: (ContentLanguage) -> Unit,
    onToggleFilter: (Tag) -> Unit,
    onRemoveFilter: (Tag) -> Unit,
    onEditText: () -> Unit,
    onRemoveText: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            ChoiceChip(
                selected = state.sort,
                options = SortOrder.entries,
                label = { it.label() },
                icon = Icons.AutoMirrored.Rounded.Sort,
                active = state.sort != SortOrder.Recent,
                onSelect = onSort
            )
        }
        item {
            ChoiceChip(
                selected = state.language,
                options = ContentLanguage.entries,
                label = { if (it == ContentLanguage.All) "All languages" else it.name },
                icon = Icons.Rounded.Translate,
                active = state.language != ContentLanguage.All,
                onSelect = onLanguage
            )
        }
        items(state.filters, key = { "${it.tag.type.key}:${it.tag.name}" }) { filter ->
            InputChip(
                selected = true,
                onClick = { onToggleFilter(filter.tag) },
                label = {
                    Text(
                        filter.tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 176.dp)
                    )
                },
                leadingIcon = {
                    Icon(
                        if (filter.excluded) Icons.Rounded.Remove else filter.tag.type.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                },
                trailingIcon = {
                    RemoveChipButton("Remove ${filter.tag.type.key} ${filter.tag.name}") {
                        onRemoveFilter(filter.tag)
                    }
                },
                colors = if (filter.excluded) InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onErrorContainer
                ) else InputChipDefaults.inputChipColors(),
                modifier = Modifier.semantics {
                    stateDescription = if (filter.excluded) "Excluded ${filter.tag.type.key}"
                    else "Included ${filter.tag.type.key}"
                }
            )
        }
        if (state.text.isNotEmpty()) {
            item {
                InputChip(
                    selected = true,
                    onClick = onEditText,
                    label = {
                        Text(
                            state.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 176.dp)
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, Modifier.size(InputChipDefaults.IconSize))
                    },
                    trailingIcon = { RemoveChipButton("Remove search text", onRemoveText) }
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceChip(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    icon: ImageVector,
    active: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label(selected)) },
            leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
            trailingIcon = {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    null,
                    Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == selected) Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Selected"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RemoveChipButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(InputChipDefaults.IconSize)) {
        Icon(Icons.Rounded.Close, description, Modifier.size(InputChipDefaults.IconSize))
    }
}

@Composable
internal fun SearchSuggestions(
    query: String,
    suggestions: List<Tag>,
    onInclude: (Tag) -> Unit,
    onExclude: (Tag) -> Unit
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (suggestions.isEmpty()) {
            item {
                Text(
                    text = if (query.isBlank()) "Type a title or tag to start searching."
                    else "No matching tags. Use Search to find galleries by title.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(suggestions, key = { "${it.type.key}:${it.name}" }) { tag ->
            ListItem(
                supportingContent = {
                    Text(if (tag.count > 0) "${tag.type.key} · ${numberFormat.format(tag.count)}" else tag.type.key)
                },
                leadingContent = { Icon(tag.type.icon(), contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onExclude(tag) }) {
                        Icon(
                            Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "Exclude ${tag.name}"
                        )
                    }
                },
                modifier = Modifier.clickable { onInclude(tag) }
            ) { Text(tag.name) }
        }
    }
}

@Composable
internal fun SearchIdleState(
    status: SearchDataStatus?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Rounded.Search,
        title = "Find something to read",
        message = "Type a title or pick tags. Long-press a result for quick actions.",
        modifier = modifier.verticalScroll(rememberScrollState()),
        action = if (status != null &&
            (status.refreshing || status.failed || status.counts.values.sum() == 0)
        ) {
            { SearchDataCard(status, onRefresh) }
        } else null
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchDataCard(status: SearchDataStatus, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                status.refreshing -> {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Downloading tag list…", style = MaterialTheme.typography.bodyMedium)
                }

                status.failed -> {
                    Text(
                        "Couldn't download the tag list.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(onClick = onRefresh) { Text("Retry") }
                }

                else -> {
                    Text(
                        "Tag suggestions need the tag list.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(onClick = onRefresh) { Text("Download tag list") }
                }
            }
        }
    }
}

private fun TagType.icon(): ImageVector = when (this) {
    TagType.Tag -> Icons.Rounded.Sell
    TagType.Artist -> Icons.Rounded.Brush
    TagType.Character -> Icons.Rounded.Person
    TagType.Parody -> Icons.Rounded.TheaterComedy
    TagType.Group -> Icons.Rounded.Groups
    TagType.Language -> Icons.Rounded.Translate
    TagType.Category -> Icons.Rounded.Category
}

private fun SortOrder.label(): String = when (this) {
    SortOrder.Recent -> "Recent"
    SortOrder.PopularToday -> "Popular today"
    SortOrder.PopularWeek -> "Popular this week"
    SortOrder.PopularMonth -> "Popular this month"
    SortOrder.PopularAllTime -> "Popular all time"
}
