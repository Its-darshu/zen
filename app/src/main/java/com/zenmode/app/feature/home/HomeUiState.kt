package com.zenmode.app.feature.home

import com.zenmode.app.domain.model.ZenDuration

/**
 * Everything the home screen draws. Immutable: the ViewModel replaces it, the
 * composable only reads it.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val currentStreak: Int = 0,
    val totalFocusSeconds: Long = 0L,
    val completedSessions: Int = 0,
    val quickPresetMinutes: List<Int> = DEFAULT_QUICK_PRESETS,
    val selectedMinutes: Int = ZenDuration.PRESET_MINUTES.first(),
    val blockedAppCount: Int = 0,
    val accessibilityEnabled: Boolean = false,
    /** False when Android has denied exact alarms, so a session may end late. */
    val exactAlarmsAvailable: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val hasActiveSession: Boolean = false,
    val showStartConfirmation: Boolean = false,
    val message: String? = null,
) {
    /** Blocking only does anything when the service is on and apps are selected. */
    val isSetUpForBlocking: Boolean
        get() = accessibilityEnabled && blockedAppCount > 0

    /** Android may run the end-of-session alarm late; the user is told so. */
    val sessionEndMayBeDelayed: Boolean
        get() = !exactAlarmsAvailable

    companion object {
        val DEFAULT_QUICK_PRESETS = listOf(25, 45, 60)
    }
}
