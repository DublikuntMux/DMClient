package com.dublikunt.dmclient.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val GroupShape = RoundedCornerShape(24.dp)
private val ItemShape = RoundedCornerShape(4.dp)

/**
 * Titled group of settings rows. Rows are separated by small gaps and the group is clipped to large
 * corners, so the first/last rows get rounded outer corners (Material 3 Expressive list style).
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        if (title != null) SectionHeader(title, Modifier.padding(start = 12.dp, bottom = 8.dp))
        Column(
            modifier = Modifier.clip(GroupShape),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) { content() }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/** Base settings row. Clickable when [onClick] is set. */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val titleColor =
        if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = ItemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                    else Modifier
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                SettingsIcon(
                    icon = icon,
                    destructive = destructive,
                    alpha = contentAlpha
                )
                Spacer(Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor.copy(alpha = contentAlpha)
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.size(12.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, destructive: Boolean, alpha: Float) {
    val container =
        if (destructive) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer
    val content =
        if (destructive) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        shape = CircleShape,
        color = container.copy(alpha = alpha),
        contentColor = content.copy(alpha = alpha),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun SettingsLinkItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null
) {
    SettingsItem(
        title = title,
        summary = summary,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingsItem(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.semantics { role = Role.Switch },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
    )
}

/** Row that opens a single-choice dialog; the summary shows the current choice. */
@Composable
fun <T> SettingsChoiceItem(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    summary: String? = null
) {
    var open by remember { mutableStateOf(false) }
    SettingsItem(
        title = title,
        summary = summary ?: label(selected),
        icon = icon,
        enabled = enabled,
        onClick = { open = true },
        modifier = modifier
    )
    if (open && enabled) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = option == selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(option)
                                        open = false
                                    }
                                )
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}

/** Row with a connected button group for 2–4 mutually exclusive options. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SettingsSegmentedItem(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        shape = ItemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    SettingsIcon(icon = icon, destructive = false, alpha = 1f)
                    Spacer(Modifier.size(16.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                options.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = option == selected,
                        onCheckedChange = { onSelect(option) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton }
                    ) {
                        Text(label(option), maxLines = 1)
                    }
                }
            }
        }
    }
}
