package com.dublikunt.dmclient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.component.StatusColorPicker
import com.dublikunt.dmclient.database.status.CustomStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import com.dublikunt.dmclient.repository.HistoryRepository
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatusesViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val statusDao: GalleryStatusDao,
) : ViewModel() {
    private val _historyList =
        MutableStateFlow<List<com.dublikunt.dmclient.database.history.GalleryHistory>>(emptyList())
    val historyList: StateFlow<List<com.dublikunt.dmclient.database.history.GalleryHistory>> =
        _historyList.asStateFlow()

    private val _statusMap = MutableStateFlow<Map<Int, GalleryStatusWithCustomStatus?>>(emptyMap())
    val statusMap: StateFlow<Map<Int, GalleryStatusWithCustomStatus?>> = _statusMap.asStateFlow()

    private val _customStatuses = MutableStateFlow<List<CustomStatus>>(emptyList())
    val customStatuses: StateFlow<List<CustomStatus>> = _customStatuses.asStateFlow()

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _historyList.value = historyRepository.getAllHistory()
            refreshStatuses()
        }
    }

    fun removeGalleryFromHistory(gallery: com.dublikunt.dmclient.database.history.GalleryHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.deleteHistory(gallery)
            _historyList.value = historyRepository.getAllHistory()
            refreshStatuses()
        }
    }

    fun createCustomStatus(name: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.insertCustomStatus(CustomStatus(name = name.trim(), color = color))
            refreshStatuses()
        }
    }

    fun updateCustomStatus(status: CustomStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.updateCustomStatus(status)
            refreshStatuses()
        }
    }

    fun deleteCustomStatus(statusId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.clearStatusFromGalleries(statusId)
            statusDao.deleteCustomStatus(statusId)
            refreshStatuses()
        }
    }

    private suspend fun refreshStatuses() {
        val historyIds = historyRepository.getAllHistory().map { it.id }
        val statuses = statusDao.getStatuses(historyIds)
        val allCustomStatuses = statusDao.getCustomStatuses()
        _statusMap.value = statuses.associateBy { it.galleryStatus.id }
        _customStatuses.value = allCustomStatuses
    }
}

@Composable
fun StatusesScreen(
    navController: NavHostController,
    viewModel: StatusesViewModel = hiltViewModel()
) {
    val historyList by viewModel.historyList.collectAsState()
    val statusMap by viewModel.statusMap.collectAsState()
    val customStatuses by viewModel.customStatuses.collectAsState()
    val scrollState = rememberLazyGridState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showManageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    val tabStatusIds =
        remember(customStatuses) { listOf<Int?>(null) + customStatuses.map { it.id } }
    if (selectedTab >= tabStatusIds.size) selectedTab = 0

    val selectedStatusId = tabStatusIds.getOrNull(selectedTab)
    val filteredHistory = remember(historyList, statusMap, searchQuery, selectedStatusId) {
        historyList.filter { galleryHistory ->
            val status = statusMap[galleryHistory.id]
            status != null && (searchQuery.isBlank() || galleryHistory.name.contains(
                searchQuery,
                ignoreCase = true
            )) &&
                    (selectedStatusId == null || status.status?.id == selectedStatusId)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Statuses", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { showManageDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Manage statuses"); Spacer(
                Modifier.width(8.dp)
            ); Text("Manage")
            }
        }

        Spacer(Modifier.height(8.dp))

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("All") }
            customStatuses.forEachIndexed { index, status ->
                Tab(
                    selected = selectedTab == index + 1,
                    onClick = { selectedTab = index + 1 }) { Text(status.name) }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(minSize = 128.dp),
            state = scrollState
        ) {
            items(filteredHistory) { galleryHistory ->
                Box(modifier = Modifier.fillMaxSize()) {
                    GalleryCard(
                        GallerySimpleInfo(
                            galleryHistory.id,
                            galleryHistory.coverUrl,
                            galleryHistory.name
                        ),
                        navController,
                        statusMap[galleryHistory.id]?.status?.name,
                        statusMap[galleryHistory.id]?.status?.color,
                        statusMap[galleryHistory.id]?.galleryStatus?.favorite ?: false
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        IconButton(onClick = { viewModel.removeGalleryFromHistory(galleryHistory) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showManageDialog) {
        ManageStatusesDialog(
            customStatuses, onDismiss = { showManageDialog = false },
            onCreate = { name, color -> viewModel.createCustomStatus(name, color) },
            onUpdate = { viewModel.updateCustomStatus(it) },
            onDelete = { viewModel.deleteCustomStatus(it) }
        )
    }
}

@Composable
private fun ManageStatusesDialog(
    statuses: List<CustomStatus>,
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit,
    onUpdate: (CustomStatus) -> Unit,
    onDelete: (Int) -> Unit
) {
    var editorStatus by remember { mutableStateOf<CustomStatus?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Statuses") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { status ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(status.name)
                        Row {
                            OutlinedButton(onClick = {
                                editorStatus = status; creatingNew = false
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit status"
                                ); Spacer(Modifier.width(8.dp)); Text("Edit")
                            }
                            Spacer(Modifier.padding(horizontal = 2.dp))
                            TextButton(
                                onClick = { onDelete(status.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete status"
                                ); Spacer(Modifier.width(8.dp)); Text("Delete")
                            }
                        }
                    }
                }
                TextButton(onClick = { editorStatus = null; creatingNew = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create status"
                    ); Spacer(Modifier.width(8.dp)); Text("Create Status")
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Check,
                    "Done"
                ); Spacer(Modifier.width(8.dp)); Text("Done")
            }
        }
    )

    if (creatingNew || editorStatus != null) {
        val existing = editorStatus
        var name by remember(existing, creatingNew) { mutableStateOf(existing?.name ?: "") }
        var selectedColor by remember(existing, creatingNew) {
            mutableIntStateOf(
                existing?.color ?: 0x00FF00
            )
        }

        AlertDialog(
            onDismissRequest = { editorStatus = null; creatingNew = false },
            title = { Text(if (existing == null) "Create Status" else "Edit Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    StatusColorPicker(selectedColor) { selectedColor = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        if (existing == null) onCreate(name.trim(), selectedColor)
                        else onUpdate(existing.copy(name = name.trim(), color = selectedColor))
                        editorStatus = null; creatingNew = false
                    }
                }) { Icon(Icons.Default.Save, "Save"); Spacer(Modifier.width(8.dp)); Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editorStatus = null; creatingNew = false
                }) {
                    Icon(
                        Icons.Default.Close,
                        "Cancel"
                    ); Spacer(Modifier.width(8.dp)); Text("Cancel")
                }
            }
        )
    }
}
