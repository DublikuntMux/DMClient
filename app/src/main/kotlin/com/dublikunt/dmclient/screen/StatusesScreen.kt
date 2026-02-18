package com.dublikunt.dmclient.screen

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.status.CustomStatus
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val historyDao = db.galleryHistoryDao()
    private val statusDao = db.galleryStatusDao()

    val historyList: LiveData<List<GalleryHistory>> = historyDao.getHistory().asLiveData()
    val statusMap = mutableStateOf<Map<Int, GalleryStatusWithCustomStatus?>>(emptyMap())
    val customStatuses = mutableStateOf<List<CustomStatus>>(emptyList())

    fun removeGalleryFromHistory(gallery: GalleryHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteHistory(gallery)
        }
    }

    fun fetchStatuses(ids: List<GalleryHistory>) {
        viewModelScope.launch(Dispatchers.IO) {
            val statuses = statusDao.getStatuses(ids.map { it.id })
            val allCustomStatuses = statusDao.getCustomStatuses()
            withContext(Dispatchers.Main) {
                statusMap.value = statuses.associateBy { it.galleryStatus.id }
                customStatuses.value = allCustomStatuses
            }
        }
    }

    fun createCustomStatus(name: String, color: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.insertCustomStatus(CustomStatus(name = name.trim(), color = color))
            refreshAllStatuses()
        }
    }

    fun updateCustomStatus(status: CustomStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.updateCustomStatus(status)
            refreshAllStatuses()
        }
    }

    fun deleteCustomStatus(statusId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            statusDao.clearStatusFromGalleries(statusId)
            statusDao.deleteCustomStatus(statusId)
            refreshAllStatuses()
        }
    }

    private suspend fun refreshAllStatuses() {
        val historyIds = historyDao.getAllHistory().map { it.id }
        val statuses = statusDao.getStatuses(historyIds)
        val allCustomStatuses = statusDao.getCustomStatuses()
        withContext(Dispatchers.Main) {
            statusMap.value = statuses.associateBy { it.galleryStatus.id }
            customStatuses.value = allCustomStatuses
        }
    }
}

@Composable
fun StatusesScreen(navController: NavHostController, viewModel: StatusesViewModel = viewModel()) {
    val historyList by viewModel.historyList.observeAsState(emptyList())
    val customStatuses by viewModel.customStatuses
    val scrollState = rememberLazyGridState()
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showManageDialog by remember { mutableStateOf(false) }

    val tabStatusIds = remember(customStatuses) {
        listOf<Int?>(null) + customStatuses.map { it.id }
    }

    if (selectedTab >= tabStatusIds.size) {
        selectedTab = 0
    }

    LaunchedEffect(historyList) {
        viewModel.fetchStatuses(historyList)
    }

    val selectedStatusId = tabStatusIds.getOrNull(selectedTab)
    val filteredHistory = remember(historyList, viewModel.statusMap.value, searchQuery, selectedStatusId) {
        historyList.filter { galleryHistory ->
            val status = viewModel.statusMap.value[galleryHistory.id]
            if (status == null) {
                false
            } else {
                val queryMatches = searchQuery.isBlank() || galleryHistory.name.contains(searchQuery, ignoreCase = true)
                val statusMatches = selectedStatusId == null || status.status?.id == selectedStatusId
                queryMatches && statusMatches
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Statuses", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

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

            Button(onClick = { showManageDialog = true }) {
                Text("Manage")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("All")
            }
            customStatuses.forEachIndexed { index, status ->
                Tab(selected = selectedTab == index + 1, onClick = { selectedTab = index + 1 }) {
                    Text(status.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(minSize = 128.dp),
            state = scrollState,
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
                        viewModel.statusMap.value[galleryHistory.id]?.status?.name,
                        viewModel.statusMap.value[galleryHistory.id]?.status?.color,
                        viewModel.statusMap.value[galleryHistory.id]?.galleryStatus?.favorite ?: false
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        IconButton(
                            onClick = { viewModel.removeGalleryFromHistory(galleryHistory) }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showManageDialog) {
        ManageStatusesDialog(
            statuses = customStatuses,
            onDismiss = { showManageDialog = false },
            onCreate = { name, color -> viewModel.createCustomStatus(name, color) },
            onUpdate = { status -> viewModel.updateCustomStatus(status) },
            onDelete = { statusId -> viewModel.deleteCustomStatus(statusId) }
        )
    }
}

@Composable
private fun ManageStatusesDialog(
    statuses: List<CustomStatus>,
    onDismiss: () -> Unit,
    onCreate: (String, Long) -> Unit,
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
                            Button(onClick = {
                                editorStatus = status
                                creatingNew = false
                            }) {
                                Text("Edit")
                            }
                            Spacer(modifier = Modifier.height(0.dp).padding(horizontal = 2.dp))
                            Button(onClick = { onDelete(status.id) }) {
                                Text("Delete")
                            }
                        }
                    }
                }

                Button(onClick = {
                    editorStatus = null
                    creatingNew = true
                }) {
                    Text("Create Status")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (creatingNew || editorStatus != null) {
        val existing = editorStatus
        var name by remember(existing, creatingNew) { mutableStateOf(existing?.name ?: "") }
        val initialColor = existing?.color?.toUInt()?.toString(16)?.uppercase()?.padStart(8, '0') ?: "FF00FF00"
        var colorInput by remember(existing, creatingNew) { mutableStateOf("#$initialColor") }

        AlertDialog(
            onDismissRequest = {
                editorStatus = null
                creatingNew = false
            },
            title = { Text(if (existing == null) "Create Status" else "Edit Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = colorInput,
                        onValueChange = { colorInput = it },
                        label = { Text("Color (#AARRGGBB or #RRGGBB)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsedColor = parseStatusColor(colorInput)
                    if (name.isNotBlank() && parsedColor != null) {
                        if (existing == null) {
                            onCreate(name.trim(), parsedColor)
                        } else {
                            onUpdate(existing.copy(name = name.trim(), color = parsedColor))
                        }
                        editorStatus = null
                        creatingNew = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = {
                    editorStatus = null
                    creatingNew = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun parseStatusColor(value: String): Long? {
    val cleaned = value.trim().removePrefix("#")
    return when (cleaned.length) {
        6 -> cleaned.toLongOrNull(16)?.let { 0xFF000000 + it }
        8 -> cleaned.toLongOrNull(16)
        else -> null
    }
}
