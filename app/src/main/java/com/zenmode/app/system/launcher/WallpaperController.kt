package com.zenmode.app.system.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.zenmode.app.di.IoDispatcher
import com.zenmode.app.domain.model.LockWallpaperCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when the system lock-screen wallpaper was changed. */
sealed interface WallpaperResult {
    data object Applied : WallpaperResult
    data object Unsupported : WallpaperResult
    data object ImageUnavailable : WallpaperResult
    data class Failed(val reason: String) : WallpaperResult
}

/**
 * The only place that touches Android's system wallpaper.
 *
 * The **home** wallpaper never comes through here: the launcher draws that
 * itself from the chosen URI, so choosing one changes nothing outside this app.
 *
 * The **lock-screen** wallpaper is different and genuinely system-wide.
 * `WallpaperManager.setStream(..., FLAG_LOCK)` is public API on every version
 * this app supports, and it really does replace the device's lock wallpaper —
 * so it is only ever called after the user has been told exactly that.
 *
 * Nothing here draws over, replaces or weakens the lock screen itself. Setting
 * a lock *wallpaper* is a picture; the keyguard, the PIN and the fingerprint
 * are untouched and belong entirely to Android.
 */
@Singleton
class WallpaperController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val wallpaperManager: WallpaperManager?
        get() = runCatching { WallpaperManager.getInstance(context) }.getOrNull()

    /**
     * Whether this device accepts a separate lock-screen wallpaper at all.
     *
     * Managed devices and some OEM builds say no, and that answer is respected
     * rather than worked around.
     */
    fun lockWallpaperCapability(): LockWallpaperCapability {
        val manager = wallpaperManager ?: return LockWallpaperCapability.UNSUPPORTED
        val allowed = runCatching {
            manager.isWallpaperSupported && manager.isSetWallpaperAllowed
        }.getOrDefault(false)
        return if (allowed) {
            LockWallpaperCapability.SUPPORTED
        } else {
            LockWallpaperCapability.UNSUPPORTED
        }
    }

    /**
     * Keeps read access to a picked image across restarts.
     *
     * Without this the URI would stop resolving the next time the process
     * starts, and the wallpaper would silently vanish after a reboot.
     */
    fun persistReadAccess(uriString: String): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            Uri.parse(uriString),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        true
    }.getOrElse {
        Log.w(TAG, "Could not persist read access to the picked image", it)
        false
    }

    /** Gives back access this app no longer needs. */
    fun releaseReadAccess(uriString: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /** True when the image behind this URI can still be opened. */
    suspend fun canOpen(uriString: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        }.getOrDefault(false)
    }

    /**
     * Replaces the device's lock-screen wallpaper with the chosen image.
     *
     * This is a real, system-wide change the user must have agreed to.
     */
    suspend fun applyLockWallpaper(uriString: String): WallpaperResult = withContext(ioDispatcher) {
        if (lockWallpaperCapability() != LockWallpaperCapability.SUPPORTED) {
            return@withContext WallpaperResult.Unsupported
        }
        val manager = wallpaperManager ?: return@withContext WallpaperResult.Unsupported

        try {
            context.contentResolver.openInputStream(Uri.parse(uriString)).use { stream ->
                if (stream == null) return@withContext WallpaperResult.ImageUnavailable
                manager.setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
            }
            WallpaperResult.Applied
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to set the lock wallpaper", e)
            WallpaperResult.Failed("Android did not allow the lock wallpaper to be changed.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set the lock wallpaper", e)
            WallpaperResult.Failed("That image could not be used as a lock-screen wallpaper.")
        }
    }

    /**
     * Clears the lock-screen wallpaper this app set.
     *
     * Android has no way to restore whatever the user had before — reading the
     * existing wallpaper is restricted, so there is nothing to back up. Clearing
     * returns the lock screen to mirroring the home wallpaper, which is
     * Android's own default, and the UI says so before the user agrees.
     */
    suspend fun clearLockWallpaper(): WallpaperResult = withContext(ioDispatcher) {
        val manager = wallpaperManager ?: return@withContext WallpaperResult.Unsupported
        try {
            manager.clear(WallpaperManager.FLAG_LOCK)
            WallpaperResult.Applied
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the lock wallpaper", e)
            WallpaperResult.Failed("Android did not allow the lock wallpaper to be cleared.")
        }
    }

    private companion object {
        const val TAG = "ZenWallpaper"
    }
}
