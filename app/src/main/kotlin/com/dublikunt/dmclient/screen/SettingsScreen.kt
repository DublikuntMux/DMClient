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
import androidx.compose.material3.AlertDialog
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
import com.dublikunt.dmclient.component.settings.SettingsButton
import com.dublikunt.dmclient.component.settings.SettingsDropdownButton
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.PreferenceHelper
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.status.GalleryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BackupData(
    val history: List<GalleryHistory>,
    val status: List<GalleryStatus>
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = AppDatabase.getDatabase(context)
    val historyDao = db.galleryHistoryDao()
    val statusDao = db.galleryStatusDao()

    var selectedLanguage by remember { mutableStateOf("all") }

    var showDeleteTokenDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearImageCacheDialog by remember { mutableStateOf(false) }
    var showClearSearchCacheDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    var showSnackbarMessage by remember { mutableStateOf<String?>(null) }

    var pinInput by remember { mutableStateOf("") }
    var pinInputError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val history = historyDao.getAllHistory()
                    val statuses = statusDao.getAllStatuses()
                    val backup = BackupData(history, statuses)
                    val json = Json.encodeToString(backup)

                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(json.toByteArray())
                    }
                    showSnackbarMessage = "Export successful"
                } catch (e: Exception) {
                    e.printStackTrace()
                    showSnackbarMessage = "Export failed: ${e.message}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        val json = input.bufferedReader().readText()
                        val backup = Json.decodeFromString<BackupData>(json)

                        if (backup.history.isNotEmpty()) {
                            historyDao.insertHistories(backup.history)
                        }
                        if (backup.status.isNotEmpty()) {
                            statusDao.insertStatuses(backup.status)
                        }
                    }
                    showSnackbarMessage = "Import successful"
                } catch (e: Exception) {
                    e.printStackTrace()
                    showSnackbarMessage = "Import failed: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val languageDeferred =
            async { PreferenceHelper.getPreferredLanguage(context).firstOrNull() }
        selectedLanguage = languageDeferred.await() ?: "all"
    }

    LaunchedEffect(showSnackbarMessage) {
        showSnackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            showSnackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
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
                    scope.launch {
                        PreferenceHelper.savePreferredLanguage(context, newLanguage)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader("Security")
                SettingsButton("Set or Change PIN Code", "Set") {
                    pinInput = ""
                    pinInputError = null
                    showPinDialog = true
                }

                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader("Data")
                SettingsButton("Export Data", "Export") {
                    exportLauncher.launch("dmclient_backup.json")
                }
                SettingsButton("Import Data", "Import") {
                    importLauncher.launch(arrayOf("application/json"))
                }

                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader("Danger Zone")
                SettingsButton("Delete Token", "Delete") {
                    showDeleteTokenDialog = true
                }
                SettingsButton("Clear History", "Clear") {
                    showClearHistoryDialog = true
                }
                SettingsButton("Clear Image Cache", "Clear") {
                    showClearImageCacheDialog = true
                }
                SettingsButton("Clear Search Cache", "Delete") {
                    showClearSearchCacheDialog = true
                }
            }
        }
    }

    if (showDeleteTokenDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTokenDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete token? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteTokenDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            PreferenceHelper.deleteTokens(context)
                        }
                        showSnackbarMessage = "Token deleted successfully."
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to clear your history? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            historyDao.deleteAllHistory()
                        }
                        showSnackbarMessage = "History cleared successfully."
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageCacheDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to clear image cache? This may remove stored images.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearImageCacheDialog = false
                    scope.launch {
                        val status = withContext(Dispatchers.IO) {
                            val cacheDir = context.cacheDir.resolve("image_cache")
                            cacheDir.deleteRecursively()
                        }
                        showSnackbarMessage =
                            if (status) "Cache cleared successfully." else "Error when clear cache!"
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearSearchCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchCacheDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to clear search cache?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearSearchCacheDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val artists = File(context.filesDir, "artists.json")
                            val characters = File(context.filesDir, "characters.json")
                            val tags = File(context.filesDir, "tags.json")

                            if (artists.exists())
                                artists.delete()
                            if (characters.exists())
                                characters.delete()
                            if (tags.exists())
                                tags.delete()
                        }
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchCacheDialog = false }) {
                    Text("Cancel")
                }
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
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }
                        },
                        label = { Text("Enter PIN (4–15 digits)") },
                        isError = pinInputError != null,
                        singleLine = true
                    )
                    pinInputError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val length = pinInput.length
                    if (length < 4 || length > 15) {
                        pinInputError = "PIN must be between 4 and 15 digits"
                        return@TextButton
                    }

                    scope.launch {
                        PreferenceHelper.savePinCode(context, pinInput)
                        showSnackbarMessage = "PIN code set successfully."
                    }
                    showPinDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    )
}
