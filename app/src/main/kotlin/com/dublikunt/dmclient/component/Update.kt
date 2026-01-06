package com.dublikunt.dmclient.component

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dublikunt.dmclient.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val RELEASE_INFO_URL =
    "https://api.github.com/repos/DublikuntMux/DMClient/releases/latest"

@Serializable
data class ReleaseInfo(
    val name: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val body: String? = null
)

private val jsonParser = Json { ignoreUnknownKeys = true }
private val okHttpClient = OkHttpClient()

suspend fun fetchLatestReleaseInfo(): ReleaseInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(RELEASE_INFO_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                response.body.string().let {
                    jsonParser.decodeFromString<ReleaseInfo>(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun getCurrentAppVersion(context: Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return packageInfo.versionName ?: "0.0.0"
}

fun isUpdateAvailable(currentVersion: String, remoteVersion: String): Boolean {
    if (currentVersion == remoteVersion) return false
    val currentParts = currentVersion.split('.').mapNotNull { it.toIntOrNull() }
    val remoteParts = remoteVersion.split('.').mapNotNull { it.toIntOrNull() }

    for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
        val currentPart = currentParts.getOrElse(i) { 0 }
        val remotePart = remoteParts.getOrElse(i) { 0 }
        if (remotePart > currentPart) return true
        if (remotePart < currentPart) return false
    }
    return false
}

@Composable
fun UpdateCheckerDialog(
    releaseInfo: ReleaseInfo,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Text("A new version (${releaseInfo.name}) of ${stringResource(id = R.string.app_name)} is available.")
                releaseInfo.body?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateClick) {
                Text("Update Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

@Composable
fun AppUpdateChecker() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var latestReleaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(key1 = Unit) {
        val currentVersion = getCurrentAppVersion(context)
        val releaseInfo = fetchLatestReleaseInfo()

        if (releaseInfo != null && isUpdateAvailable(currentVersion, releaseInfo.name)) {
            latestReleaseInfo = releaseInfo
            showDialog = true
        }
    }

    if (showDialog && latestReleaseInfo != null) {
        UpdateCheckerDialog(
            releaseInfo = latestReleaseInfo!!,
            onDismiss = { showDialog = false },
            onUpdateClick = {
                showDialog = false
                try {
                    val intent = Intent(Intent.ACTION_VIEW, latestReleaseInfo!!.htmlUrl.toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
    }
}