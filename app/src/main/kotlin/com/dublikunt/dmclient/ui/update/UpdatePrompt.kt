package com.dublikunt.dmclient.ui.update

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.ui.components.MarkdownText
import com.dublikunt.dmclient.ui.components.userMessage

@Composable
fun UpdatePrompt() {
    val viewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(viewModel) { viewModel.checkOnStart() }
    UpdateDialog(viewModel)
}

@Composable
fun ManualUpdatePrompt() {
    val viewModel: UpdateViewModel = hiltViewModel()
    UpdateDialog(viewModel)
}

@Composable
private fun UpdateDialog(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.apk) {
        state.apk?.let { file ->
            try {
                val uri =
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
                viewModel.installedIntentLaunched()
            } catch (error: Exception) {
                viewModel.installFailed(error.userMessage())
            }
        }
    }
    val release = state.release ?: return
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    release.name.ifBlank { release.tagName },
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(
                        release.body.orEmpty().ifBlank { "A new version of DMClient is available." }
                    )
                }
                if (state.downloading) {
                    LinearWavyProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                state.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::download, enabled = !state.downloading) {
                Text(if (state.error != null) "Retry" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss, enabled = !state.downloading) { Text("Later") }
        },
    )
}
