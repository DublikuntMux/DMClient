package com.dublikunt.dmclient.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LabelOff
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.data.repository.StorageUsage
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.ui.components.ConfirmDialog
import com.dublikunt.dmclient.ui.components.ErrorState
import com.dublikunt.dmclient.ui.components.LoadingState
import com.dublikunt.dmclient.ui.components.SettingsChoiceItem
import com.dublikunt.dmclient.ui.components.SettingsGroup
import com.dublikunt.dmclient.ui.components.SettingsItem
import com.dublikunt.dmclient.ui.components.StatusDot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageScreen(onBack: () -> Unit) {
    val viewModel: StorageViewModel = hiltViewModel()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Storage") },
                scrollBehavior = scroll,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                val current = usage
                when {
                    error != null ->
                        ErrorState(
                            error!!,
                            onRetry = viewModel::refresh,
                            modifier = Modifier.height(240.dp),
                        )

                    current == null -> LoadingState(Modifier.height(160.dp))
                    else -> UsageCard(current)
                }
            }
            item {
                SettingsGroup("Image cache") {
                    SettingsChoiceItem(
                        "Maximum cache size",
                        SettingsRepository.IMAGE_CACHE_SIZE_OPTIONS,
                        settings.imageCacheSize,
                        { Formatter.formatFileSize(context, it) },
                        viewModel::setCacheSize,
                        icon = Icons.Rounded.DataUsage,
                        summary =
                            "${
                                Formatter.formatFileSize(
                                    context,
                                    settings.imageCacheSize
                                )
                            } · Applies after restart",
                    )
                    SettingsItem(
                        "Clear image cache",
                        icon = Icons.Rounded.CleaningServices,
                        destructive = true,
                        onClick = { confirmation = "cache" },
                    )
                }
            }
            item {
                SettingsGroup("Clear data") {
                    SettingsItem(
                        "Clear history",
                        icon = Icons.Rounded.History,
                        destructive = true,
                        onClick = { confirmation = "history" },
                    )
                    SettingsItem(
                        "Delete all downloads",
                        icon = Icons.Rounded.Delete,
                        destructive = true,
                        onClick = { confirmation = "downloads" },
                    )
                    SettingsItem(
                        "Clear tag list",
                        icon = Icons.AutoMirrored.Rounded.LabelOff,
                        destructive = true,
                        onClick = { confirmation = "tags" },
                    )
                }
            }
        }
    }
    confirmation?.let { target ->
        val title =
            when (target) {
                "cache" -> "Clear image cache"
                "history" -> "Clear history"
                "downloads" -> "Delete all downloads"
                else -> "Clear tag list"
            }
        val message =
            when (target) {
                "cache" -> "Cached images will be removed and loaded again when needed."
                "history" -> "All reading history and saved page positions will be removed."
                "downloads" -> "All downloaded files and downloads in progress will be removed."
                else ->
                    "Tag suggestions will be unavailable until you download or import a tag list."
            }
        ConfirmDialog(
            "$title?",
            message,
            title,
            {
                when (target) {
                    "cache" -> viewModel.clearCache()
                    "history" -> viewModel.clearHistory()
                    "downloads" -> viewModel.deleteDownloads()
                    else -> viewModel.clearTags()
                }
            },
            { confirmation = null },
            destructive = true,
        )
    }
}

@Composable
private fun UsageCard(usage: StorageUsage) {
    val context = LocalContext.current
    val fractions = storageFractions(usage.downloadsBytes, usage.imageCacheBytes)
    Surface(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(
                    Formatter.formatFileSize(context, usage.downloadsBytes + usage.imageCacheBytes),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Total used",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (fractions.downloads > 0f)
                    Box(
                        Modifier
                            .weight(fractions.downloads)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                if (fractions.imageCache > 0f)
                    Box(
                        Modifier
                            .weight(fractions.imageCache)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
            }
            StorageLegend(
                "Downloads · ${usage.downloadsCount}",
                Formatter.formatFileSize(context, usage.downloadsBytes),
                MaterialTheme.colorScheme.primary,
            )
            StorageLegend(
                "Image cache",
                Formatter.formatFileSize(context, usage.imageCacheBytes),
                MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "${usage.historyCount} history items · ${usage.searchEntries} tag entries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageLegend(label: String, size: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(color, size = 12.dp)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(size, style = MaterialTheme.typography.labelLarge)
    }
}
