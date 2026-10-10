package com.dublikunt.dmclient.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.data.settings.GridDensity
import com.dublikunt.dmclient.data.settings.LockTimeout
import com.dublikunt.dmclient.data.settings.ReaderMode
import com.dublikunt.dmclient.data.settings.ThemeMode
import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.ui.components.SettingsChoiceItem
import com.dublikunt.dmclient.ui.components.SettingsGroup
import com.dublikunt.dmclient.ui.components.SettingsItem
import com.dublikunt.dmclient.ui.components.SettingsLinkItem
import com.dublikunt.dmclient.ui.components.SettingsSegmentedItem
import com.dublikunt.dmclient.ui.components.SettingsSwitchItem
import com.dublikunt.dmclient.ui.library.relativeTime
import com.dublikunt.dmclient.ui.update.ManualUpdatePrompt
import java.text.NumberFormat
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(onOpenStorage: () -> Unit, onOpenStatuses: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val pinDialog by viewModel.pinDialog.collectAsStateWithLifecycle()
    val tagStatus by viewModel.tagStatus.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pinOptions by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val exportBackup =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let(viewModel::exportBackup)
        }
    val importBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importBackup)
        }
    val importTags =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importTags)
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshUsage() }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbar.showSnackbar(event.text)
                is SettingsEvent.CopyReport -> {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("DMClient crash report", event.report)
                    )
                    snackbar.showSnackbar("Crash report copied")
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { LargeFlexibleTopAppBar(title = { Text("Settings") }, scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup("Appearance") {
                    SettingsSegmentedItem(
                        "Theme",
                        ThemeMode.entries,
                        settings.themeMode,
                        { it.name },
                        { value -> viewModel.changeSetting { setThemeMode(value) } },
                        icon = Icons.Rounded.Palette,
                    )
                    if (Build.VERSION.SDK_INT >= 31)
                        SettingsSwitchItem(
                            "Dynamic color",
                            settings.dynamicColor,
                            { value -> viewModel.changeSetting { setDynamicColor(value) } },
                            icon = Icons.Rounded.ColorLens,
                        )
                    SettingsSwitchItem(
                        "Pure black",
                        settings.pureBlack,
                        { value -> viewModel.changeSetting { setPureBlack(value) } },
                        summary = "Only in dark theme",
                        icon = Icons.Rounded.DarkMode,
                    )
                    SettingsChoiceItem(
                        "Grid size",
                        GridDensity.entries,
                        settings.gridDensity,
                        { it.name },
                        { value -> viewModel.changeSetting { setGridDensity(value) } },
                        icon = Icons.Rounded.GridView,
                    )
                }
            }
            item {
                SettingsGroup("Browsing") {
                    SettingsChoiceItem(
                        "Content language",
                        ContentLanguage.entries,
                        settings.language,
                        { it.name },
                        { value -> viewModel.changeSetting { setLanguage(value) } },
                        icon = Icons.Rounded.Language,
                    )
                    SettingsSwitchItem(
                        "Record history",
                        settings.recordHistory,
                        { value -> viewModel.changeSetting { setRecordHistory(value) } },
                        summary = "Save galleries you open to History",
                        icon = Icons.Rounded.History,
                    )
                }
            }
            item {
                SettingsGroup("Reader") {
                    SettingsChoiceItem(
                        "Reading mode",
                        ReaderMode.entries,
                        settings.readerMode,
                        {
                            when (it) {
                                ReaderMode.PagedLtr -> "Left to right"
                                ReaderMode.PagedRtl -> "Right to left"
                                ReaderMode.Vertical -> "Vertical scroll"
                            }
                        },
                        { value -> viewModel.changeSetting { setReaderMode(value) } },
                        icon = Icons.Rounded.MenuBook,
                    )
                    SettingsSwitchItem(
                        "Keep screen on",
                        settings.keepScreenOn,
                        { value -> viewModel.changeSetting { setKeepScreenOn(value) } },
                        icon = Icons.Rounded.WbSunny,
                    )
                }
            }
            item {
                SettingsGroup("Library") {
                    SettingsLinkItem(
                        "Reading statuses",
                        onOpenStatuses,
                        icon = Icons.Rounded.Bookmarks,
                    )
                }
            }
            item {
                SettingsGroup("Security") {
                    SettingsItem(
                        "App lock",
                        summary =
                            when (pinSet) {
                                null -> "Loading…"
                                true -> "PIN set"
                                false -> "Off"
                            },
                        icon = Icons.Rounded.Lock,
                        enabled = pinSet != null,
                        onClick = {
                            if (pinSet == true) pinOptions = true
                            else viewModel.openPinDialog(PinAction.Set)
                        },
                    )
                    SettingsChoiceItem(
                        "Lock timeout",
                        LockTimeout.entries,
                        settings.lockTimeout,
                        {
                            when (it) {
                                LockTimeout.Immediate -> "Immediately"
                                LockTimeout.OneMinute -> "After 1 minute"
                                LockTimeout.FiveMinutes -> "After 5 minutes"
                                LockTimeout.OnRestart -> "Only on app restart"
                            }
                        },
                        { value -> viewModel.changeSetting { setLockTimeout(value) } },
                        icon = Icons.Rounded.Timer,
                        enabled = pinSet == true,
                    )
                    SettingsSwitchItem(
                        "Hide content in recents",
                        settings.secureScreen,
                        { value -> viewModel.changeSetting { setSecureScreen(value) } },
                        icon = Icons.Rounded.VisibilityOff,
                    )
                }
            }
            item {
                SettingsGroup("Data") {
                    SettingsLinkItem(
                        "Storage",
                        onOpenStorage,
                        summary =
                            usage?.let {
                                "${Formatter.formatFileSize(context, it.downloadsBytes + it.imageCacheBytes)} used"
                            } ?: "Calculating…",
                        icon = Icons.Rounded.Storage,
                    )
                    SettingsItem(
                        "Export backup",
                        icon = Icons.Rounded.UploadFile,
                        onClick = { exportBackup.launch(backupFileName(LocalDate.now())) },
                    )
                    SettingsItem(
                        "Import backup",
                        icon = Icons.Rounded.FileOpen,
                        onClick = { importBackup.launch(arrayOf("application/json")) },
                    )
                    val status = tagStatus
                    val summary =
                        when {
                            status == null -> "Loading…"
                            status.refreshing -> "Updating…"
                            status.failed -> "Update failed"
                            status.counts.values.sum() == 0 -> "Not downloaded"
                            else -> {
                                val count =
                                    NumberFormat.getIntegerInstance()
                                        .format(status.counts.values.sum())
                                "$count entries" +
                                    (status.lastUpdated?.let {
                                        " · updated ${relativeTime(it, now).replaceFirstChar { ch -> ch.lowercaseChar() }}"
                                    } ?: "")
                            }
                        }
                    SettingsItem(
                        "Tag list",
                        summary = summary,
                        icon = Icons.Rounded.Label,
                        enabled = status?.refreshing == false,
                        onClick = viewModel::refreshTags,
                    )
                    SettingsItem(
                        "Import tag list file",
                        icon = Icons.Rounded.FileDownload,
                        onClick = { importTags.launch(arrayOf("application/json")) },
                    )
                }
            }
            item {
                SettingsGroup("About") {
                    SettingsItem(
                        "Version",
                        summary = viewModel.versionName,
                        icon = Icons.Rounded.Info,
                    )
                    SettingsSwitchItem(
                        "Check for updates automatically",
                        settings.checkUpdates,
                        { value -> viewModel.changeSetting { setCheckUpdates(value) } },
                        icon = Icons.Rounded.SystemUpdate,
                    )
                    SettingsItem(
                        "Check now",
                        summary = if (checking) "Checking…" else null,
                        icon = Icons.Rounded.Refresh,
                        enabled = !checking,
                        onClick = viewModel::checkUpdates,
                    )
                    SettingsItem(
                        "Copy last crash report",
                        icon = Icons.Rounded.BugReport,
                        onClick = viewModel::copyCrashReport,
                    )
                    SettingsLinkItem(
                        "Source code",
                        {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/DublikuntMux/DMClient".toUri(),
                                    )
                                )
                            } catch (_: Exception) {
                                scope.launch {
                                    snackbar.showSnackbar("Couldn't open the source code page.")
                                }
                            }
                        },
                        icon = Icons.Rounded.Code,
                    )
                }
            }
        }
    }
    if (pinOptions)
        AlertDialog(
            onDismissRequest = { pinOptions = false },
            icon = { Icon(Icons.Rounded.Lock, null) },
            title = { Text("App lock") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            pinOptions = false
                            viewModel.openPinDialog(PinAction.Change)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Change PIN")
                    }
                    TextButton(
                        onClick = {
                            pinOptions = false
                            viewModel.openPinDialog(PinAction.Remove)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pinOptions = false }) { Text("Close") } },
        )
    pinDialog?.let { dialog ->
        PinEntryDialog(dialog, onSave = viewModel::savePin, onDismiss = viewModel::closePinDialog)
    }
    if (!settings.checkUpdates) ManualUpdatePrompt()
}
