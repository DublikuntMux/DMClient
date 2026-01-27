package com.dublikunt.dmclient.component.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun SettingsButton(
    title: String,
    buttonText: String,
    onClick: () -> Unit
) {
    BaseSettingsItem(title = title) {
        TextButton(
            onClick = onClick,
        ) {
            Text(
                buttonText,
            )
        }
    }
}
