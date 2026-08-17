package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.WallpaperSettings
import com.zenmode.app.domain.repository.WallpaperRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads the launcher's wallpaper choices. */
class GetWallpaperSettingsUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
) {
    operator fun invoke(): Flow<WallpaperSettings> = wallpaperRepository.observeSettings()

    suspend fun current(): WallpaperSettings = wallpaperRepository.getSettings()
}

/**
 * Changes the launcher's wallpaper choices.
 *
 * Only the *stored choice* is changed here. Actually replacing the device's
 * lock-screen wallpaper is a system operation and lives in the Android layer,
 * because it affects the whole phone rather than just this app.
 */
class UpdateWallpaperUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
) {
    suspend fun setHome(uri: String) = wallpaperRepository.setHomeWallpaper(uri)

    suspend fun clearHome() = wallpaperRepository.clearHomeWallpaper()

    suspend fun setLock(uri: String) = wallpaperRepository.setLockWallpaper(uri)

    suspend fun clearLock() = wallpaperRepository.clearLockWallpaper()

    /** The chosen home image can no longer be opened; stop trying to draw it. */
    suspend fun invalidateHome() = wallpaperRepository.invalidateHomeWallpaper()
}
