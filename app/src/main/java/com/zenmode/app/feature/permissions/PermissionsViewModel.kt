package com.zenmode.app.feature.permissions

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.usecase.UpdateSettingsUseCase
import com.zenmode.app.system.SystemSettingsLauncher
import com.zenmode.app.system.ZenSetupStatus
import com.zenmode.app.system.ZenSetupStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionsUiState(
    val isLoading: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val blockedAppCount: Int = 0,
    val exactAlarmsAvailable: Boolean = true,
    val notificationsEnabled: Boolean = true,
    /** Below Android 12 exact alarms need no permission, so there is nothing to ask for. */
    val exactAlarmSettingExists: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val blockingReady: Boolean get() = accessibilityEnabled && blockedAppCount > 0
}

/**
 * Backs both the first-run explanation and the permissions screen in settings.
 *
 * It reports what Android currently allows and can open the settings screens
 * where the user decides. It cannot grant anything — every one of these is the
 * user's call, made in Android's own UI.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val updateSettings: UpdateSettingsUseCase,
    private val setupStatusProvider: ZenSetupStatusProvider,
    private val settingsLauncher: SystemSettingsLauncher,
) : ViewModel() {

    val uiState: StateFlow<PermissionsUiState> = setupStatusProvider.observe()
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PermissionsUiState(),
        )

    /** Re-reads the states Android does not broadcast. */
    fun refresh() {
        setupStatusProvider.refresh()
    }

    /** @return false when the device has no such settings screen to open. */
    fun openAccessibilitySettings(): Boolean = settingsLauncher.openAccessibilitySettings()

    fun openExactAlarmSettings(): Boolean = settingsLauncher.openExactAlarmSettings()

    fun openNotificationSettings(): Boolean = settingsLauncher.openNotificationSettings()

    /** Remembers that the explanation has been shown, whatever the user chose. */
    fun onOnboardingFinished() {
        viewModelScope.launch { updateSettings.setOnboardingCompleted(true) }
    }

    private fun ZenSetupStatus.toUiState() = PermissionsUiState(
        isLoading = false,
        accessibilityEnabled = accessibilityEnabled,
        blockedAppCount = blockedAppCount,
        exactAlarmsAvailable = exactAlarmsAvailable,
        notificationsEnabled = notificationsEnabled,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
