package com.zenmode.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.zenmode.app.domain.model.ZenSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes settings in DataStore.
 *
 * A corrupted or unreadable store falls back to defaults rather than throwing:
 * settings must never be the reason the app fails to open (specification §34).
 */
@Singleton
class SettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<ZenSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences.toZenSettings() }

    suspend fun getSettings(): ZenSettings = settings.first()

    suspend fun setDefaultDurationMinutes(minutes: Int) = edit { it[Keys.DEFAULT_DURATION] = minutes }

    suspend fun setConfirmStart(enabled: Boolean) = edit { it[Keys.CONFIRM_START] = enabled }

    suspend fun setCompletionNotification(enabled: Boolean) =
        edit { it[Keys.COMPLETION_NOTIFICATION] = enabled }

    suspend fun setPureBlackZenScreen(enabled: Boolean) = edit { it[Keys.PURE_BLACK] = enabled }

    suspend fun setShowClock(enabled: Boolean) = edit { it[Keys.SHOW_CLOCK] = enabled }

    suspend fun setShowDate(enabled: Boolean) = edit { it[Keys.SHOW_DATE] = enabled }

    suspend fun setUse24HourClock(enabled: Boolean) = edit { it[Keys.USE_24_HOUR_CLOCK] = enabled }

    suspend fun setShowCallButton(enabled: Boolean) = edit { it[Keys.SHOW_CALL_BUTTON] = enabled }

    suspend fun setStrictMode(enabled: Boolean) = edit { it[Keys.STRICT_MODE] = enabled }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[Keys.ONBOARDING_COMPLETED] = completed }

    /**
     * Clears every setting so the defaults apply again. Onboarding state is kept:
     * resetting preferences should not re-run the permission explanation.
     */
    suspend fun resetToDefaults() = edit { preferences ->
        val onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED]
        preferences.clear()
        if (onboardingCompleted != null) preferences[Keys.ONBOARDING_COMPLETED] = onboardingCompleted
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // Writing settings is never worth crashing over; the previous value stands.
        }
    }

    private fun Preferences.toZenSettings(): ZenSettings {
        val defaults = ZenSettings()
        return ZenSettings(
            defaultDurationMinutes = this[Keys.DEFAULT_DURATION] ?: defaults.defaultDurationMinutes,
            confirmStart = this[Keys.CONFIRM_START] ?: defaults.confirmStart,
            completionNotification = this[Keys.COMPLETION_NOTIFICATION]
                ?: defaults.completionNotification,
            pureBlackZenScreen = this[Keys.PURE_BLACK] ?: defaults.pureBlackZenScreen,
            showClock = this[Keys.SHOW_CLOCK] ?: defaults.showClock,
            showDate = this[Keys.SHOW_DATE] ?: defaults.showDate,
            use24HourClock = this[Keys.USE_24_HOUR_CLOCK] ?: defaults.use24HourClock,
            showCallButton = this[Keys.SHOW_CALL_BUTTON] ?: defaults.showCallButton,
            strictMode = this[Keys.STRICT_MODE] ?: defaults.strictMode,
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
        )
    }

    private object Keys {
        val DEFAULT_DURATION = intPreferencesKey("default_duration_minutes")
        val CONFIRM_START = booleanPreferencesKey("confirm_start")
        val COMPLETION_NOTIFICATION = booleanPreferencesKey("completion_notification")
        val PURE_BLACK = booleanPreferencesKey("pure_black_zen_screen")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val USE_24_HOUR_CLOCK = booleanPreferencesKey("use_24_hour_clock")
        val SHOW_CALL_BUTTON = booleanPreferencesKey("show_call_button")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    companion object {
        const val DATASTORE_NAME = "zen_settings"
    }
}
