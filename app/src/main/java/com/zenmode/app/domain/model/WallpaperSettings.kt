package com.zenmode.app.domain.model

/**
 * The launcher's wallpaper choices (launcher spec §4).
 *
 * URIs are held as plain strings, never as `android.net.Uri`, so the domain
 * layer stays free of Android types. Nothing but a reference and a flag is
 * stored — no bytes, no bitmaps, no drawables.
 */
data class WallpaperSettings(
    val homeEnabled: Boolean = false,
    val homeUri: String? = null,
    val lockEnabled: Boolean = false,
    val lockUri: String? = null,
) {
    /** True when the home screen should try to draw an image behind its content. */
    val hasHomeWallpaper: Boolean get() = homeEnabled && !homeUri.isNullOrBlank()

    val hasLockWallpaper: Boolean get() = lockEnabled && !lockUri.isNullOrBlank()
}

/** What this device will let the app do to the system lock-screen wallpaper. */
enum class LockWallpaperCapability {
    /** `WallpaperManager` accepts a separate lock-screen image. */
    SUPPORTED,

    /**
     * The device reports wallpaper changes as unsupported or disallowed — some
     * managed and OEM configurations do. Nothing is attempted in that case.
     */
    UNSUPPORTED,
}
