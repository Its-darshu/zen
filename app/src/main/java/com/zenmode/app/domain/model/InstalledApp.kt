package com.zenmode.app.domain.model

/** An app installed on the device that the user could choose to block. */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
)

/**
 * A row on the blocked-apps screen: an installed app plus whether the user has
 * it switched on.
 */
data class SelectableApp(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isSystemApp: Boolean = false,
)
