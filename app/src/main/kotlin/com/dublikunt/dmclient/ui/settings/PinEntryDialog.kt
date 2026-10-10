package com.dublikunt.dmclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun PinEntryDialog(
    state: PinDialogState,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(state.action) { mutableStateOf("") }
    var pin by remember(state.action) { mutableStateOf("") }
    var confirmation by remember(state.action) { mutableStateOf("") }
    val title =
        when (state.action) {
            PinAction.Set -> "Set PIN"
            PinAction.Change -> "Change PIN"
            PinAction.Remove -> "Remove PIN"
        }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.action != PinAction.Set)
                    PinField("Current PIN", current, { current = it }, state)
                if (state.action != PinAction.Remove) {
                    PinField("New PIN", pin, { pin = it }, state)
                    PinField("Confirm PIN", confirmation, { confirmation = it }, state)
                    Text(
                        "Use 4–15 digits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(current, pin, confirmation) }, enabled = !state.busy) {
                Text(if (state.busy) "Saving…" else title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") }
        },
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    state: PinDialogState,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 15 && it.all { ch -> ch in '0'..'9' }) onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = !state.busy,
        isError = state.error != null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
