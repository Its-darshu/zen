package com.zenmode.app.domain.repository

import com.zenmode.app.domain.model.ZenSettings
import kotlinx.coroutines.flow.Flow

/** User settings, stored locally in DataStore. */
interface SettingsRepository {

    fun observeSettings(): Flow<ZenSettings>

    suspend fun getSettings(): ZenSettings

    suspend fun setDefaultDurationMinutes(minutes: Int)

    suspend fun setConfirmStart(enabled: Boolean)

    suspend fun setCompletionNotification(enabled: Boolean)

    suspend fun setPureBlackZenScreen(enabled: Boolean)

    suspend fun setShowClock(enabled: Boolean)

    suspend fun setShowDate(enabled: Boolean)

    suspend fun setUse24HourClock(enabled: Boolean)

    suspend fun setShowCallButton(enabled: Boolean)

    suspend fun setConfirmExit(enabled: Boolean)

    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Restores every setting to its default. */
    suspend fun resetToDefaults()
}
