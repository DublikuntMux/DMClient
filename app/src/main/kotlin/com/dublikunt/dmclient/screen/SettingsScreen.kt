package com.dublikunt.dmclient.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = AppDatabase.getDatabase(context)
    val historyDao = db.galleryHistoryDao()

    var selectedLanguage by remember { mutableStateOf("all") }

    var showDeleteTokenDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearImageCacheDialog by remember { mutableStateOf(false) }
    var showClearTagsCacheDialog by remember { mutableStateOf(false) }
    var showSnackbarMessage by remember { mutableStateOf<String?>(null) }

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
                SettingsButton("Clear Tags Cache", "Delete") {
                    showClearTagsCacheDialog = true
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

    if (showClearTagsCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearTagsCacheDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to clear tags cache? This may remove stored images.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearTagsCacheDialog = false
                    scope.launch {
                        val status = withContext(Dispatchers.IO) {
                            val tags = File(context.filesDir, "tags.json")
                            if (tags.exists()) {
                                tags.delete()
                            } else {
                                true
                            }
                        }
                        showSnackbarMessage =
                            if (status) "Cache cleared successfully." else "Error when clear cache!"
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTagsCacheDialog = false }) {
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
