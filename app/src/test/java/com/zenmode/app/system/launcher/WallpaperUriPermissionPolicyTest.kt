package com.zenmode.app.system.launcher

import com.zenmode.app.domain.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which persisted URI grants get handed back.
 *
 * Both directions can hurt. Never releasing leaves the app holding standing
 * read access to every image the user ever picked, and Android caps how many
 * such grants an app may hold, so eventually picking a new wallpaper starts
 * failing. Releasing too eagerly makes a wallpaper that is still in use vanish
 * at the next reboot. These tests pin down the line between the two.
 */
class WallpaperUriPermissionPolicyTest {

    private val old = "content://docs/old"
    private val new = "content://docs/new"
    private val other = "content://docs/other"

    // ---- replacing an image ----

    @Test
    fun `replacing the home image releases the one it replaced`() {
        val previous = WallpaperSettings(homeEnabled = true, homeUri = old)

        assertEquals(old, WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, new))
    }

    @Test
    fun `replacing the lock image releases the one it replaced`() {
        val previous = WallpaperSettings(lockEnabled = true, lockUri = old)

        assertEquals(old, WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.LOCK, new))
    }

    @Test
    fun `changing one wallpaper never releases the other one's image`() {
        val previous = WallpaperSettings(
            homeEnabled = true, homeUri = old,
            lockEnabled = true, lockUri = other,
        )

        assertEquals(old, WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, new))
        assertEquals(
            other,
            WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.LOCK, new),
        )
    }

    @Test
    fun `an image both wallpapers use is kept when only one of them changes`() {
        // Releasing here would break the other wallpaper at the next reboot.
        val previous = WallpaperSettings(
            homeEnabled = true, homeUri = old,
            lockEnabled = true, lockUri = old,
        )

        assertNull(WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, new))
        assertNull(WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.LOCK, new))
    }

    @Test
    fun `re-picking the same image releases nothing`() {
        // Releasing and immediately re-taking the same grant is a window in
        // which access can be lost for no benefit at all.
        val previous = WallpaperSettings(homeEnabled = true, homeUri = old)

        assertNull(WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, old))
    }

    // ---- clearing ----

    @Test
    fun `clearing releases the image that was in use`() {
        val previous = WallpaperSettings(homeEnabled = true, homeUri = old)

        assertEquals(
            old,
            WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, newUri = null),
        )
    }

    @Test
    fun `clearing an empty slot releases nothing, so a second clear is a no-op`() {
        // This is what prevents a double release: after the first clear the slot
        // holds nothing, so there is nothing left to hand back.
        val cleared = WallpaperSettings()

        assertNull(WallpaperUriPermissionPolicy.staleUri(cleared, WallpaperSlot.HOME, null))
        assertNull(WallpaperUriPermissionPolicy.staleUri(cleared, WallpaperSlot.LOCK, null))
    }

    @Test
    fun `clearing a shared image keeps it for the wallpaper still using it`() {
        val previous = WallpaperSettings(
            homeEnabled = true, homeUri = old,
            lockEnabled = true, lockUri = old,
        )

        assertNull(WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.LOCK, null))
    }

    @Test
    fun `a blank stored reference is never released`() {
        val previous = WallpaperSettings(homeUri = "   ")

        assertNull(WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, new))
    }

    @Test
    fun `a switched-off wallpaper still gives its grant back`() {
        // The switch is a display choice; the grant is real either way.
        val previous = WallpaperSettings(homeEnabled = false, homeUri = old)

        assertEquals(old, WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, null))
    }

    // ---- a grant taken for an operation that then failed ----

    @Test
    fun `a grant taken for a failed change is handed straight back`() {
        assertEquals(
            new,
            WallpaperUriPermissionPolicy.unusedClaim(WallpaperSettings(), new),
        )
    }

    @Test
    fun `a failed change keeps the grant when that image is already in use`() {
        val current = WallpaperSettings(homeEnabled = true, homeUri = new)

        assertNull(WallpaperUriPermissionPolicy.unusedClaim(current, new))
    }

    @Test
    fun `a failed lock change keeps a grant the lock wallpaper already relies on`() {
        val current = WallpaperSettings(lockEnabled = true, lockUri = new)

        assertNull(WallpaperUriPermissionPolicy.unusedClaim(current, new))
    }

    @Test
    fun `a blank claim is never released`() {
        assertNull(WallpaperUriPermissionPolicy.unusedClaim(WallpaperSettings(), ""))
    }
}
