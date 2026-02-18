package com.dublikunt.dmclient.component.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class SettingsButtonType {
    Filled,
    FilledTonal,
    Outlined,
    Elevated,
    Text
}

@Composable
fun SettingsButton(
    title: String,
    buttonText: String,
    icon: ImageVector? = null,
    buttonType: SettingsButtonType = SettingsButtonType.FilledTonal,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        null
    }

    BaseSettingsItem(title = title) {
        when (buttonType) {
            SettingsButtonType.Filled -> Button(
                onClick = onClick,
                colors = if (contentColor != null) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = buttonText)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(buttonText)
            }

            SettingsButtonType.FilledTonal -> FilledTonalButton(
                onClick = onClick,
                colors = if (contentColor != null) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                }
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = buttonText)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(buttonText)
            }

            SettingsButtonType.Outlined -> OutlinedButton(
                onClick = onClick,
                colors = if (contentColor != null) {
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = buttonText)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(buttonText)
            }

            SettingsButtonType.Elevated -> ElevatedButton(
                onClick = onClick,
                colors = if (contentColor != null) {
                    ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    ButtonDefaults.elevatedButtonColors()
                }
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = buttonText)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(buttonText)
            }

            SettingsButtonType.Text -> TextButton(
                onClick = onClick,
                colors = if (contentColor != null) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = buttonText)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(buttonText)
            }
        }
    }
}
