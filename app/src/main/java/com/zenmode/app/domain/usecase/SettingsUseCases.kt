package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.domain.repository.SessionRepository
import com.zenmode.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads user settings (specification §16, §22). */
class GetSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<ZenSettings> = settingsRepository.observeSettings()

    suspend fun current(): ZenSettings = settingsRepository.getSettings()
}

/**
 * Writes user settings. One entry point per setting so a screen cannot
 * accidentally overwrite the whole object with stale values.
 */
class UpdateSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun setDefaultDuration(minutes: Int) =
        settingsRepository.setDefaultDurationMinutes(minutes)

    suspend fun setConfirmStart(enabled: Boolean) = settingsRepository.setConfirmStart(enabled)

    suspend fun setCompletionNotification(enabled: Boolean) =
        settingsRepository.setCompletionNotification(enabled)

    suspend fun setPureBlackZenScreen(enabled: Boolean) =
        settingsRepository.setPureBlackZenScreen(enabled)

    suspend fun setShowClock(enabled: Boolean) = settingsRepository.setShowClock(enabled)

    suspend fun setShowDate(enabled: Boolean) = settingsRepository.setShowDate(enabled)

    suspend fun setUse24HourClock(enabled: Boolean) = settingsRepository.setUse24HourClock(enabled)

    suspend fun setShowCallButton(enabled: Boolean) = settingsRepository.setShowCallButton(enabled)

    suspend fun setStrictMode(enabled: Boolean) = settingsRepository.setStrictMode(enabled)

    suspend fun setOnboardingCompleted(completed: Boolean) =
        settingsRepository.setOnboardingCompleted(completed)
}

/**
 * Deletes every stored session.
 *
 * Statistics and streaks are derived from the session history, so this clears
 * those too — there is no separate pile of numbers to reset. Destructive:
 * always confirm first (specification §16).
 */
class ClearHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke() = sessionRepository.clearHistory()
}
