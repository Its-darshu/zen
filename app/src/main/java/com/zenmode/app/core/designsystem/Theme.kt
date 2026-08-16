package com.zenmode.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZenColorScheme = darkColorScheme(
    primary = ZenTextPrimary,
    onPrimary = ZenBlack,
    secondary = ZenTextSecondary,
    onSecondary = ZenBlack,
    background = ZenBlack,
    onBackground = ZenTextPrimary,
    surface = ZenSurface,
    onSurface = ZenTextPrimary,
    surfaceVariant = ZenSurfaceElevated,
    onSurfaceVariant = ZenTextSecondary,
    outline = ZenDivider,
    outlineVariant = ZenDivider,
    error = ZenAlert,
    onError = ZenTextPrimary,
)

/**
 * There is no light theme by design: Zen Mode is a black screen on purpose, and
 * a dynamic-colour version would undo that.
 */
@Composable
fun ZenModeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZenColorScheme,
        typography = ZenTypography,
        content = content,
    )
}
