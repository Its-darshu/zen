package com.zenmode.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shared building blocks. Everything is monochrome, flat and generously
 * spaced: no gradients, no elevation, no colour that is not black, white or one
 * of the two greys.
 *
 * Touch targets are at least 48dp and text sizes are in sp, so the whole app
 * stays usable with a large system font and TalkBack.
 */

/** Minimum touch target, per the Android accessibility guidelines. */
val ZenMinTouchTarget = 48.dp

val ZenScreenPadding = 24.dp

@Composable
fun ZenTopBar(
    title: String?,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minWidth = ZenMinTouchTarget, minHeight = ZenMinTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ZenTextPrimary,
                )
            }
        } else {
            Spacer(Modifier.width(ZenScreenPadding - 8.dp))
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = ZenTextPrimary,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/** The one filled button in the app: the action the screen exists for. */
@Composable
fun ZenPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val background = if (enabled) ZenTextPrimary else ZenSurfaceElevated
    val foreground = if (enabled) ZenBlack else ZenTextSecondary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(background, RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            textAlign = TextAlign.Center,
        )
    }
}

/** An outlined alternative, for secondary choices. */
@Composable
fun ZenSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ZenMinTouchTarget),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) ZenTextPrimary else ZenDivider),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) ZenSurfaceElevated else Color.Transparent,
            contentColor = if (selected) ZenTextPrimary else ZenTextSecondary,
            disabledContentColor = ZenTextSecondary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ZenSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = ZenTextSecondary,
        modifier = modifier.padding(vertical = 12.dp),
    )
}

/** A label above a value: the statistics and home read-outs. */
@Composable
fun ZenStatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // One announcement per block, rather than three disconnected strings.
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(label, value, supporting).joinToString(", ")
            }
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = ZenTextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = ZenTextPrimary,
        )
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
            )
        }
    }
}

/** A settings row with a switch. The whole row is the touch target. */
@Composable
fun ZenSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ZenMinTouchTarget)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) ZenTextPrimary else ZenTextSecondary,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenTextSecondary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            // The row already carries the label and the toggle state.
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZenBlack,
                checkedTrackColor = ZenTextPrimary,
                checkedBorderColor = ZenTextPrimary,
                uncheckedThumbColor = ZenTextSecondary,
                uncheckedTrackColor = ZenSurfaceElevated,
                uncheckedBorderColor = ZenDivider,
            ),
        )
    }
}

/** A tappable settings row that opens something else. */
@Composable
fun ZenNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ZenMinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = ZenTextPrimary,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenTextSecondary,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = ZenTextSecondary,
            )
        }
    }
}

@Composable
fun ZenHorizontalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = ZenDivider)
}

/** What a screen shows when there is genuinely nothing to show. */
@Composable
fun ZenEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ZenTextPrimary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A confirmation. Used for starting a session, stopping one, and the
 * destructive settings actions — never to trick the user into staying.
 */
@Composable
fun ZenConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    /**
     * An optional third action, for fixing the thing the dialog is warning
     * about — "enable blocking" rather than just accepting that it is off.
     */
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZenSurfaceElevated,
        titleContentColor = ZenTextPrimary,
        textContentColor = ZenTextSecondary,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (destructive) ZenAlert else ZenTextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (neutralText != null && onNeutral != null) {
                    TextButton(onClick = onNeutral) {
                        Text(
                            text = neutralText,
                            color = ZenTextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissText,
                        color = ZenTextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    )
}

/** A small status line, e.g. whether blocking is currently able to work. */
@Composable
fun ZenStatusBanner(
    text: String,
    actionText: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZenSurfaceElevated, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ZenTextSecondary,
        )
        if (actionText != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .heightIn(min = ZenMinTouchTarget)
                    .clickable(role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = actionText.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = ZenTextPrimary,
                )
            }
        }
    }
}
