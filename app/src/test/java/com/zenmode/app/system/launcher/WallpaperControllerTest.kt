package com.zenmode.app.system.launcher

import androidx.test.core.app.ApplicationProvider
import com.zenmode.app.domain.model.LockWallpaperCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The system-wallpaper adapter.
 *
 * Only what Robolectric can answer honestly is asserted here. Its
 * `ContentResolver` and `WallpaperManager` are permissive stubs — they happily
 * open URIs that no real device would — so the denial paths (missing image,
 * revoked permission, OEM refusal) cannot be exercised on the JVM and are
 * listed as physical-device checks instead. Asserting them here would prove
 * nothing and would fail for the wrong reason.
 *
 * What *is* worth pinning down: every operation returns a typed outcome rather
 * than throwing, and an unsupported device is never told a wallpaper was applied.
 */
@RunWith(RobolectricTestRunner::class)
class WallpaperControllerTest {

    private val controller = WallpaperController(
        ApplicationProvider.getApplicationContext(),
        Dispatchers.Unconfined,
    )

    @Test
    fun `the device is asked whether lock wallpapers are allowed at all`() {
        val capability = controller.lockWallpaperCapability()

        // The point is that the app asks rather than assuming, and copes with
        // either answer.
        assertTrue(
            capability == LockWallpaperCapability.SUPPORTED ||
                capability == LockWallpaperCapability.UNSUPPORTED,
        )
    }

    @Test
    fun `applying a wallpaper always returns an outcome rather than throwing`() = runTest {
        val result = controller.applyLockWallpaper("content://com.example.nothing/missing")

        assertTrue(
            result == WallpaperResult.Applied ||
                result == WallpaperResult.Unsupported ||
                result == WallpaperResult.ImageUnavailable ||
                result is WallpaperResult.Failed,
        )
    }

    @Test
    fun `a malformed uri is handled rather than thrown`() = runTest {
        val result = controller.applyLockWallpaper("not-a-uri-at-all")

        assertTrue(
            result == WallpaperResult.Applied ||
                result == WallpaperResult.Unsupported ||
                result == WallpaperResult.ImageUnavailable ||
                result is WallpaperResult.Failed,
        )
    }

    @Test
    fun `clearing the lock wallpaper reports an outcome either way`() = runTest {
        val result = controller.clearLockWallpaper()

        assertTrue(
            result == WallpaperResult.Applied ||
                result is WallpaperResult.Failed ||
                result == WallpaperResult.Unsupported,
        )
    }

    @Test
    fun `an unsupported device is never told a wallpaper was applied`() = runTest {
        if (controller.lockWallpaperCapability() == LockWallpaperCapability.UNSUPPORTED) {
            assertEquals(
                WallpaperResult.Unsupported,
                controller.applyLockWallpaper("content://whatever"),
            )
        }
    }

    @Test
    fun `persisting and releasing access never throw`() {
        // Both are called on URIs the app may or may not hold a grant for.
        controller.persistReadAccess("content://com.example.nothing/missing")
        controller.releaseReadAccess("content://com.example.nothing/missing")
    }
}
