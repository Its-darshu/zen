package com.zenmode.app.domain.model

/**
 * An application the user has chosen to keep out of reach during a Zen session.
 *
 * A row exists for every app the user has ever toggled; [enabled] says whether
 * it is currently part of the blocklist, so unchecking an app does not lose the
 * fact that the user knows about it.
 */
data class BlockedApp(
    val packageName: String,
    val appName: String,
    val enabled: Boolean,
)
