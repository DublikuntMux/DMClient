package com.dublikunt.dmclient.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.crash.CrashReporter
import com.dublikunt.dmclient.data.lock.AppLockManager
import com.dublikunt.dmclient.data.repository.BackupRepository
import com.dublikunt.dmclient.data.repository.SearchDataRepository
import com.dublikunt.dmclient.data.repository.StorageRepository
import com.dublikunt.dmclient.data.repository.StorageUsage
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.ui.components.userMessage
import com.dublikunt.dmclient.ui.update.UpdateCheckResult
import com.dublikunt.dmclient.ui.update.UpdatePromptState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent

    data class CopyReport(val report: String) : SettingsEvent
}

enum class PinAction {
    Set,
    Change,
    Remove,
}

data class PinDialogState(
    val action: PinAction,
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val repository: SettingsRepository,
    private val storage: StorageRepository,
    private val backup: BackupRepository,
    private val searchData: SearchDataRepository,
    private val lock: AppLockManager,
    private val updates: UpdatePromptState,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings = repository.settings
    private val pinRefresh = MutableStateFlow(0)
    val pinSet =
        pinRefresh
            .flatMapLatest { lock.isPinSet }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tagStatus =
        searchData.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableUsage = MutableStateFlow<StorageUsage?>(null)
    val usage = mutableUsage.asStateFlow()
    private val mutablePinDialog = MutableStateFlow<PinDialogState?>(null)
    val pinDialog = mutablePinDialog.asStateFlow()
    private val mutableChecking = MutableStateFlow(false)
    val checking = mutableChecking.asStateFlow()
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val versionName: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun refreshUsage() = action { mutableUsage.value = storage.usage() }

    fun changeSetting(block: suspend SettingsRepository.() -> Unit) = action { repository.block() }

    fun refreshTags() = action { searchData.refresh() }

    fun exportBackup(uri: Uri) = action {
        val stream =
            context.contentResolver.openOutputStream(uri) ?: error("Couldn't open the backup file.")
        stream.use { backup.export(it) }
        message("Backup exported")
    }

    fun importBackup(uri: Uri) = action {
        val stream =
            context.contentResolver.openInputStream(uri) ?: error("Couldn't open the backup file.")
        val result = stream.use { backup.import(it) }
        mutableUsage.value = storage.usage()
        message(
            "Imported ${result.galleries} galleries, ${result.library} library entries, ${result.statuses} statuses, and ${result.history} history items"
        )
    }

    fun importTags(uri: Uri) = action {
        val stream =
            context.contentResolver.openInputStream(uri)
                ?: error("Couldn't open the tag list file.")
        val count = stream.use { searchData.importBundle(it) }
        mutableUsage.value = storage.usage()
        message("Imported $count tag entries")
    }

    fun copyCrashReport() = action {
        val report = CrashReporter.consumePendingReport(context)
        if (report == null) message("No crash report saved")
        else eventChannel.send(SettingsEvent.CopyReport(report))
    }

    fun checkUpdates() {
        if (mutableChecking.value) return
        mutableChecking.value = true
        action {
            try {
                when (updates.check()) {
                    UpdateCheckResult.Available -> Unit
                    UpdateCheckResult.Current -> message("You're up to date")
                    UpdateCheckResult.Failed -> message("Couldn't check for updates. Try again.")
                }
            } finally {
                mutableChecking.value = false
            }
        }
    }

    fun openPinDialog(action: PinAction) {
        mutablePinDialog.value = PinDialogState(action)
    }

    fun closePinDialog() {
        if (mutablePinDialog.value?.busy != true) mutablePinDialog.value = null
    }

    fun savePin(current: String, pin: String, confirmation: String) {
        val dialog = mutablePinDialog.value ?: return
        if (dialog.busy) return
        val error =
            if (dialog.action == PinAction.Remove) validatePinForm(current)
            else
                validatePinForm(pin, confirmation)
                    ?: if (dialog.action == PinAction.Change) validatePinForm(current) else null
        if (error != null) {
            mutablePinDialog.value = dialog.copy(error = error)
            return
        }
        mutablePinDialog.value = dialog.copy(busy = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dialog.action != PinAction.Set && !lock.verify(current)) {
                    mutablePinDialog.value = dialog.copy(error = "Wrong PIN")
                    return@launch
                }
                if (dialog.action == PinAction.Remove) lock.removePin() else lock.setPin(pin)
                pinRefresh.update { it + 1 }
                mutablePinDialog.value = null
                message(if (dialog.action == PinAction.Remove) "PIN removed" else "PIN saved")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutablePinDialog.value = dialog.copy(error = error.userMessage())
            }
        }
    }

    private suspend fun message(text: String) {
        eventChannel.send(SettingsEvent.Message(text))
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message(error.userMessage())
            }
        }
    }
}
