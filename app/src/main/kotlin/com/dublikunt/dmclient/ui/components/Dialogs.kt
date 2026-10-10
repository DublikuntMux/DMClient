package com.dublikunt.dmclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Preset ARGB colors offered for reading statuses. */
val StatusPalette: List<Int> = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
    0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
    0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
    0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFF6D4C41,
).map { it.toInt() }

/** Create/edit dialog for a reading status: name field plus a swatch palette. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusEditorDialog(
    title: String,
    onSave: (name: String, color: Int) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialColor: Int = StatusPalette.first()
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    val valid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 8
                ) {
                    val palette = if (initialColor in StatusPalette) StatusPalette
                    else listOf(initialColor) + StatusPalette
                    palette.forEach { swatch ->
                        ColorSwatch(
                            color = Color(swatch),
                            selected = swatch == color,
                            onClick = { color = swatch }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name.trim(), color)
                    onDismiss()
                },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .padding(if (selected) 4.dp else 0.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
