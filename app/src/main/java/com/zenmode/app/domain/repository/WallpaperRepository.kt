package com.zenmode.app.domain.repository

import com.zenmode.app.domain.model.WallpaperSettings
import kotlinx.coroutines.flow.Flow

/** Stores which images the user picked, and whether each is switched on. */
interface WallpaperRepository {

    fun observeSettings(): Flow<WallpaperSettings>

    suspend fun getSettings(): WallpaperSettings

    /** Records a chosen home image and switches the home wallpaper on. */
    suspend fun setHomeWallpaper(uri: String)

    /** Switches the home wallpaper off. The chosen image is forgotten with it. */
    suspend fun clearHomeWallpaper()

    suspend fun setLockWallpaper(uri: String)

    suspend fun clearLockWallpaper()

    /**
     * Forgets a home image that can no longer be opened — deleted, or its
     * permission revoked — so the launcher stops retrying it every draw.
     */
    suspend fun invalidateHomeWallpaper()
}
