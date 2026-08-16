package com.zenmode.app.data.repository

import com.zenmode.app.data.local.datastore.SettingsDataSource
import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataSource: SettingsDataSource,
) : SettingsRepository {

    override fun observeSettings(): Flow<ZenSettings> = settingsDataSource.settings

    override suspend fun getSettings(): ZenSettings = settingsDataSource.getSettings()

    override suspend fun setDefaultDurationMinutes(minutes: Int) =
        settingsDataSource.setDefaultDurationMinutes(minutes)

    override suspend fun setConfirmStart(enabled: Boolean) =
        settingsDataSource.setConfirmStart(enabled)

    override suspend fun setCompletionNotification(enabled: Boolean) =
        settingsDataSource.setCompletionNotification(enabled)

    override suspend fun setPureBlackZenScreen(enabled: Boolean) =
        settingsDataSource.setPureBlackZenScreen(enabled)

    override suspend fun setShowClock(enabled: Boolean) = settingsDataSource.setShowClock(enabled)

    override suspend fun setShowDate(enabled: Boolean) = settingsDataSource.setShowDate(enabled)

    override suspend fun setUse24HourClock(enabled: Boolean) =
        settingsDataSource.setUse24HourClock(enabled)

    override suspend fun setShowCallButton(enabled: Boolean) =
        settingsDataSource.setShowCallButton(enabled)

    override suspend fun setStrictMode(enabled: Boolean) =
        settingsDataSource.setStrictMode(enabled)

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        settingsDataSource.setOnboardingCompleted(completed)

    override suspend fun resetToDefaults() = settingsDataSource.resetToDefaults()
}
