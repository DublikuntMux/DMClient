package com.dublikunt.dmclient.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import com.dublikunt.dmclient.data.repository.UpdateRepository
import com.dublikunt.dmclient.di.ApplicationScope
import com.dublikunt.dmclient.network.ReleaseInfo
import com.dublikunt.dmclient.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateUiState(
    val release: ReleaseInfo? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val apk: File? = null,
)

enum class UpdateCheckResult {
    Available,
    Current,
    Failed,
}

@Singleton
class UpdatePromptState
@Inject
constructor(
    private val repository: UpdateRepository,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state = mutableState.asStateFlow()
    private val checkMutex = Mutex()
    private var startupChecked = false

    fun checkOnStart() {
        synchronized(this) {
            if (startupChecked) return
            startupChecked = true
        }
        scope.launch(Dispatchers.IO) { check() }
    }

    suspend fun check(): UpdateCheckResult = checkMutex.withLock {
        val release = repository.latestRelease() ?: return@withLock UpdateCheckResult.Failed
        val current =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        if (repository.isNewer(current, release.tagName)) {
            if (!mutableState.value.downloading)
                mutableState.value = UpdateUiState(release = release)
            UpdateCheckResult.Available
        } else UpdateCheckResult.Current
    }

    fun dismiss() {
        if (!mutableState.value.downloading) mutableState.value = UpdateUiState()
    }

    fun download() {
        val current = mutableState.value
        val release = current.release ?: return
        if (current.downloading) return
        mutableState.value =
            current.copy(downloading = true, progress = 0f, error = null, apk = null)
        scope.launch(Dispatchers.IO) {
            try {
                val file =
                    repository.downloadApk(release) { progress ->
                        mutableState.update { it.copy(progress = progress.coerceIn(0f, 1f)) }
                    }
                mutableState.update { it.copy(downloading = false, progress = 1f, apk = file) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(downloading = false, error = error.userMessage()) }
            }
        }
    }

    fun installFailed(message: String) {
        mutableState.update { it.copy(apk = null, error = message) }
    }

    fun installedIntentLaunched() {
        mutableState.value = UpdateUiState()
    }
}

@HiltViewModel
class UpdateViewModel @Inject constructor(private val prompt: UpdatePromptState) : ViewModel() {
    val state = prompt.state

    fun checkOnStart() = prompt.checkOnStart()

    fun dismiss() = prompt.dismiss()

    fun download() = prompt.download()

    fun installFailed(message: String) = prompt.installFailed(message)

    fun installedIntentLaunched() = prompt.installedIntentLaunched()
}
