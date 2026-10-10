package com.dublikunt.dmclient.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(64.dp))
    }
}

/**
 * Centered illustration-style message: an icon inside an expressive shape, a title, an optional
 * message and an optional action.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = iconContainerColor,
                contentColor = iconColor,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    onRetry: (() -> Unit)? = null
) {
    EmptyState(
        icon = Icons.Rounded.CloudOff,
        title = title,
        message = message,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier,
        action = onRetry?.let {
            {
                FilledTonalButton(onClick = it) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Retry")
                }
            }
        }
    )
}

/** Single-row error for list footers. */
@Composable
fun InlineError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
