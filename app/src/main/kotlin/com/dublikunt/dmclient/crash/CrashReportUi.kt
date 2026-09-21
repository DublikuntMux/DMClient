package com.dublikunt.dmclient.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private fun copyReportToClipboard(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("DMClient crash report", report))
    Toast.makeText(context, "Crash report copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "DMClient crash report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Share crash report"))
}

private fun openIssuesPage(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, CRASH_ISSUES_URL.toUri()))
    } catch (_: Exception) {
    }
}

@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var pendingReport by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        pendingReport = CrashReporter.consumePendingReport(context)
    }

    val report = pendingReport ?: return
    var showDetails by remember { mutableStateOf(false) }

    fun dismiss() {
        CrashReporter.clearPendingReport(context)
        pendingReport = null
    }

    if (!showDetails) {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("The app crashed last time") },
            text = {
                Text(
                    "A crash report was saved. Copy it and attach it to a GitHub issue " +
                            "to help fix the problem."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    copyReportToClipboard(context, report)
                    dismiss()
                }) { Text("Copy report") }
            },
            dismissButton = {
                TextButton(onClick = { showDetails = true }) { Text("Details") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("Crash report") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            text = report,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyReportToClipboard(context, report)
                    dismiss()
                }) { Text("Copy") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        shareReport(context, report)
                    }) { Text("Share") }
                    TextButton(onClick = {
                        openIssuesPage(context)
                    }) { Text("Open GitHub issues") }
                    TextButton(onClick = { dismiss() }) { Text("Dismiss") }
                }
            }
        )
    }
}
