package com.zenmode.app.data.repository

import com.zenmode.app.data.local.datastore.WallpaperSettingsDataSource
import com.zenmode.app.domain.model.WallpaperSettings
import com.zenmode.app.domain.repository.WallpaperRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    private val dataSource: WallpaperSettingsDataSource,
) : WallpaperRepository {

    override fun observeSettings(): Flow<WallpaperSettings> = dataSource.settings

    override suspend fun getSettings(): WallpaperSettings = dataSource.getSettings()

    override suspend fun setHomeWallpaper(uri: String) {
        if (uri.isBlank()) return
        dataSource.setHome(uri = uri, enabled = true)
    }

    override suspend fun clearHomeWallpaper() = dataSource.setHome(uri = null, enabled = false)

    override suspend fun setLockWallpaper(uri: String) {
        if (uri.isBlank()) return
        dataSource.setLock(uri = uri, enabled = true)
    }

    override suspend fun clearLockWallpaper() = dataSource.setLock(uri = null, enabled = false)

    /**
     * The image is gone. Switching the wallpaper off — rather than keeping a
     * URI that will fail again — is what stops the home screen retrying a dead
     * reference on every draw, and lets the UI say plainly that it needs a new
     * image.
     */
    override suspend fun invalidateHomeWallpaper() = dataSource.setHome(uri = null, enabled = false)
}
