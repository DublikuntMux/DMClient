package com.dublikunt.dmclient.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dublikunt.dmclient.component.settings.SettingsButton
import com.dublikunt.dmclient.component.settings.SettingsButtonType
import com.dublikunt.dmclient.component.settings.SettingsDropdownButton
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.database.search.SearchCacheDao
import com.dublikunt.dmclient.database.status.CustomStatus
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.repository.PreferenceRepository
import com.dublikunt.dmclient.scrapper.NHentaiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

@Serializable
data class BackupData(
    val history: List<com.dublikunt.dmclient.database.history.GalleryHistory>,
    val galleryStatuses: List<GalleryStatus>,
    val customStatuses: List<CustomStatus>
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val galleryHistoryDao: GalleryHistoryDao,
    private val galleryStatusDao: GalleryStatusDao,
    private val searchCacheDao: SearchCacheDao,
    private val nHentaiApi: NHentaiApi,
) : ViewModel() {
    suspend fun getPreferredLanguage(): String =
        preferenceRepository.preferredLanguage.first() ?: "all"

    fun savePreferredLanguage(language: String) =
        viewModelScope.launch { preferenceRepository.savePreferredLanguage(language) }

    fun deleteTokens() = viewModelScope.launch {
        preferenceRepository.deleteTokens()
        nHentaiApi.clearCookies()
    }
    fun savePinCode(pin: String) = viewModelScope.launch { preferenceRepository.savePinCode(pin) }

    fun clearSearchCache(filesDir: File) = viewModelScope.launch(Dispatchers.IO) {
        searchCacheDao.deleteAll()
        listOf("artists.json", "characters.json", "tags.json", "parodies.json")
            .forEach { name -> File(filesDir, name).delete() }
    }

    suspend fun exportData(): BackupData = withContext(Dispatchers.IO) {
        val history = galleryHistoryDao.getAllHistory()
        val galleryStatuses = galleryStatusDao.getAllGalleryStatusEntities()
        val customStatuses = galleryStatusDao.getCustomStatuses()
        BackupData(history, galleryStatuses, customStatuses)
    }

    suspend fun importData(backup: BackupData) = withContext(Dispatchers.IO) {
        if (backup.history.isNotEmpty()) galleryHistoryDao.insertHistories(backup.history)
        if (backup.customStatuses.isNotEmpty()) galleryStatusDao.insertCustomStatuses(backup.customStatuses)
        if (backup.galleryStatuses.isNotEmpty()) galleryStatusDao.insertStatuses(backup.galleryStatuses)
    }
}

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedLanguage by remember { mutableStateOf("all") }
    var showDeleteTokenDialog by remember { mutableStateOf(false) }
    var showClearSearchCacheDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showSnackbarMessage by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinInputError by remember { mutableStateOf<String?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    try {
                        val backup = viewModel.exportData()
                        val json = Json.encodeToString(backup)
                        context.contentResolver.openOutputStream(it)
                            ?.use { output -> output.write(json.toByteArray()) }
                        showSnackbarMessage = "Export successful"
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showSnackbarMessage = "Export failed: ${e.message}"
                    }
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    try {
                        val jsonStr = context.contentResolver.openInputStream(it)
                            ?.use { input -> input.bufferedReader().readText() } ?: return@launch
                        val backup = Json.decodeFromString<BackupData>(jsonStr)
                        viewModel.importData(backup)
                        showSnackbarMessage = "Import successful"
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showSnackbarMessage = "Import failed: ${e.message}"
                    }
                }
            }
        }

    LaunchedEffect(Unit) { selectedLanguage = viewModel.getPreferredLanguage() }

    LaunchedEffect(showSnackbarMessage) {
        showSnackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            showSnackbarMessage = null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)

                SettingsSectionHeader("General")
                val languages = listOf("all", "english", "japanese", "chinese")
                SettingsDropdownButton(
                    "Preferred Language",
                    selectedLanguage,
                    languages
                ) { newLanguage ->
                    selectedLanguage = newLanguage
                    scope.launch { viewModel.savePreferredLanguage(newLanguage) }
                }

                Spacer(Modifier.height(16.dp))
                SettingsSectionHeader("Security")
                SettingsButton(
                    "Set or Change PIN Code",
                    "Set",
                    Icons.Filled.Lock,
                    SettingsButtonType.Filled
                ) {
                    pinInput = ""; pinInputError = null; showPinDialog = true
                }

                Spacer(Modifier.height(16.dp))
                SettingsSectionHeader("Data")
                SettingsButton(
                    "Storage Management",
                    "Open",
                    Icons.Filled.Storage,
                    SettingsButtonType.Outlined
                ) { navController.navigate("storage") }
                SettingsButton(
                    "Export Data",
                    "Export",
                    Icons.Filled.Upload,
                    SettingsButtonType.FilledTonal
                ) { exportLauncher.launch("dmclient_backup.json") }
                SettingsButton(
                    "Import Data",
                    "Import",
                    Icons.Filled.Download,
                    SettingsButtonType.FilledTonal
                ) { importLauncher.launch(arrayOf("application/json")) }

                Spacer(Modifier.height(16.dp))
                SettingsSectionHeader("Danger Zone")
                SettingsButton(
                    "Delete Token",
                    "Delete",
                    Icons.Filled.Delete,
                    SettingsButtonType.Text,
                    isDestructive = true
                ) { showDeleteTokenDialog = true }
                SettingsButton(
                    "Clear Search Cache",
                    "Delete",
                    Icons.Filled.Delete,
                    SettingsButtonType.Text,
                    isDestructive = true
                ) { showClearSearchCacheDialog = true }
            }
        }
    }

    if (showDeleteTokenDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTokenDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete token? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteTokenDialog = false
                        viewModel.deleteTokens()
                        showSnackbarMessage = "Token deleted successfully."
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteTokenDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    if (showClearSearchCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchCacheDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to clear search cache?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearSearchCacheDialog = false
                        viewModel.clearSearchCache(context.filesDir)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearSearchCacheDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set PIN Code") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Enter PIN (4–15 digits)") },
                        isError = pinInputError != null,
                        singleLine = true
                    )
                    pinInputError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput.length in 4..15) {
                        viewModel.savePinCode(pinInput)
                        showSnackbarMessage = "PIN code set successfully."
                        showPinDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        title, style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    )
}
