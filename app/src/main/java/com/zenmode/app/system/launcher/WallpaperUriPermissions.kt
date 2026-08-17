package com.zenmode.app.system.launcher

import com.zenmode.app.domain.model.WallpaperSettings

/** Which wallpaper a chosen image was picked for. */
enum class WallpaperSlot { HOME, LOCK }

/**
 * Decides which persisted URI grants the app should give back.
 *
 * A grant taken with `takePersistableUriPermission` survives reboots and stays
 * on the app's permission list until it is released or the app is uninstalled.
 * Taking one for every image the user ever tries and never releasing any leaves
 * a growing list of documents this app can still read — long after it has any
 * reason to — and Android caps that list, so eventually new picks silently stop
 * working. Both are reasons to hand grants back.
 *
 * Getting it wrong in the other direction is worse: releasing a URI that is
 * still in use makes the wallpaper vanish at the next reboot. So the rules here
 * are deliberately conservative, and pure, so every one of them can be proven.
 */
internal object WallpaperUriPermissionPolicy {

    /**
     * The grant that is no longer needed once [slot] changes from what
     * [previous] recorded to [newUri] (null when the wallpaper is being
     * cleared), or null when nothing should be released.
     *
     * Returns null when:
     * - the slot held nothing, so there is nothing to release — this is also
     *   what makes a second clear a no-op rather than a double release;
     * - the URI has not actually changed, since releasing and immediately
     *   re-taking a grant is a window in which access can be lost for nothing;
     * - the other wallpaper still points at the same image, in which case the
     *   grant is shared and releasing it would break that one.
     */
    fun staleUri(
        previous: WallpaperSettings,
        slot: WallpaperSlot,
        newUri: String?,
    ): String? {
        val held = previous.uriFor(slot)?.takeUnless { it.isBlank() } ?: return null
        if (held == newUri) return null
        if (held == previous.uriFor(slot.other())) return null
        return held
    }

    /**
     * A grant that was taken but never stored — because the operation it was
     * taken for failed — and should therefore be handed straight back.
     *
     * Null when the settings already point at that same image, since the grant
     * is then still doing a job.
     */
    fun unusedClaim(current: WallpaperSettings, uri: String): String? = uri
        .takeUnless { it.isBlank() || it == current.homeUri || it == current.lockUri }

    private fun WallpaperSettings.uriFor(slot: WallpaperSlot): String? = when (slot) {
        WallpaperSlot.HOME -> homeUri
        WallpaperSlot.LOCK -> lockUri
    }

    private fun WallpaperSlot.other(): WallpaperSlot = when (this) {
        WallpaperSlot.HOME -> WallpaperSlot.LOCK
        WallpaperSlot.LOCK -> WallpaperSlot.HOME
    }
}
