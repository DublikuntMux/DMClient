package com.dublikunt.dmclient.screen

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.component.BaseDialog
import com.dublikunt.dmclient.component.settings.SettingsButton
import com.dublikunt.dmclient.component.settings.SettingsButtonType
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.repository.DownloadRepository
import com.dublikunt.dmclient.repository.PreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val preferenceRepository: PreferenceRepository,
    private val galleryHistoryDao: GalleryHistoryDao,
) : ViewModel() {
    private val _imageCacheSize = MutableStateFlow(0L)
    val imageCacheSize: StateFlow<Long> = _imageCacheSize.asStateFlow()

    private val _historyCount = MutableStateFlow(0)
    val historyCount: StateFlow<Int> = _historyCount.asStateFlow()

    private val _maxCacheSize = MutableStateFlow(1024L * 1024 * 1024)
    val maxCacheSize: StateFlow<Long> = _maxCacheSize.asStateFlow()

    val downloadedGalleries = downloadRepository.getAll()

    init {
        refreshStats()
        viewModelScope.launch {
            _maxCacheSize.value =
                preferenceRepository.maxImageCacheSize.first() ?: (1024L * 1024 * 1024)
        }
    }

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _imageCacheSize.value = calculateCacheSize()
            _historyCount.value = galleryHistoryDao.getAllHistory().size
        }
    }

    private fun calculateCacheSize(): Long {
        val cacheDir = File(context.cacheDir, "image_cache")
        return if (cacheDir.exists()) cacheDir.walkTopDown().filter { it.isFile }
            .sumOf { it.length() } else 0L
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            File(context.cacheDir, "image_cache").deleteRecursively()
            _imageCacheSize.value = 0L
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            galleryHistoryDao.deleteAllHistory()
            _historyCount.value = 0
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) { downloadRepository.deleteAll() }
    }

    fun deleteDownloadedGallery(gallery: DownloadedGallery) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.delete(gallery)
            File(gallery.coverPath).parentFile?.deleteRecursively()
        }
    }

    fun setMaxCacheSize(size: Long) {
        viewModelScope.launch {
            _maxCacheSize.value = size
            preferenceRepository.saveMaxImageCacheSize(size)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(viewModel: StorageViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val imageCacheSize by viewModel.imageCacheSize.collectAsState()
    val historyCount by viewModel.historyCount.collectAsState()
    val downloads by viewModel.downloadedGalleries.collectAsState(initial = emptyList())
    val maxCacheSize by viewModel.maxCacheSize.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Storage Management") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StorageInfoRow(
                            "Image Cache",
                            Formatter.formatFileSize(context, imageCacheSize)
                        )
                        StorageInfoRow("History", "$historyCount items")
                        StorageInfoRow("Downloads", "${downloads.size} items")
                    }
                }
            }

            item {
                Column {
                    Text("Maximum Cache Size: ${Formatter.formatFileSize(context, maxCacheSize)}")
                    Slider(
                        value = maxCacheSize.toFloat(),
                        onValueChange = { viewModel.setMaxCacheSize(it.toLong()) },
                        valueRange = (100f * 1024 * 1024)..(5f * 1024 * 1024 * 1024),
                        steps = 49
                    )
                }
                SettingsButton(
                    "Image Cache",
                    "Clear All",
                    Icons.Default.Image,
                    SettingsButtonType.Outlined,
                    isDestructive = true
                ) { showClearCacheDialog = true }
                SettingsButton(
                    "History",
                    "Clear All",
                    Icons.Default.History,
                    SettingsButtonType.Outlined,
                    isDestructive = true
                ) { showClearHistoryDialog = true }
                SettingsButton(
                    "Downloads",
                    "Clear All",
                    Icons.Default.Download,
                    SettingsButtonType.Outlined,
                    isDestructive = true
                ) { showClearDownloadsDialog = true }
            }

            items(downloads) { gallery ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                gallery.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                "${gallery.totalPages} pages",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { viewModel.deleteDownloadedGallery(gallery) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) BaseDialog(
        title = "Clear History",
        text = { Text("Are you sure you want to clear all history?") },
        confirmText = "Confirm",
        dismissText = "Cancel",
        isDestructiveConfirm = true,
        onConfirm = { viewModel.clearHistory(); showClearHistoryDialog = false },
        onDismiss = { showClearHistoryDialog = false }
    )
    if (showClearCacheDialog) BaseDialog(
        title = "Clear Image Cache",
        text = { Text("Are you sure you want to clear the image cache?") },
        confirmText = "Confirm",
        dismissText = "Cancel",
        isDestructiveConfirm = true,
        onConfirm = { viewModel.clearImageCache(); showClearCacheDialog = false },
        onDismiss = { showClearCacheDialog = false }
    )
    if (showClearDownloadsDialog) BaseDialog(
        title = "Clear Downloads",
        text = { Text("Are you sure you want to delete all downloaded galleries?") },
        confirmText = "Confirm",
        dismissText = "Cancel",
        isDestructiveConfirm = true,
        onConfirm = { viewModel.clearAllDownloads(); showClearDownloadsDialog = false },
        onDismiss = { showClearDownloadsDialog = false }
    )
}

@Composable
fun StorageInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label); Text(value, fontWeight = FontWeight.SemiBold)
    }
}
