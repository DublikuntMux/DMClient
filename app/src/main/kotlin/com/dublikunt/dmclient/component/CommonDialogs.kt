package com.dublikunt.dmclient.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructiveConfirm: Boolean = false,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel"
) {
    BaseDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
        isDestructiveConfirm = isDestructiveConfirm,
        dismissText = dismissText,
        text = { Text(text) }
    )
}
