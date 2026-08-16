package com.zenmode.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.ZenDuration
import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.domain.usecase.CheckAccessibilityPermissionUseCase
import com.zenmode.app.domain.usecase.ClearHistoryUseCase
import com.zenmode.app.domain.usecase.GetBlockedAppsUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.domain.usecase.UpdateSettingsUseCase
import com.zenmode.app.system.LockdownCapability
import com.zenmode.app.system.ZenLockdownController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: ZenSettings = ZenSettings(),
    val blockedAppCount: Int = 0,
    val accessibilityEnabled: Boolean = false,
    /** What Android will actually enforce on this device if strict mode is on. */
    val lockdownCapability: LockdownCapability = LockdownCapability.SCREEN_PINNING,
    val durationOptions: List<Int> = ZenDuration.PRESET_MINUTES,
    val showClearHistoryConfirmation: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updateSettings: UpdateSettingsUseCase,
    private val clearHistory: ClearHistoryUseCase,
    private val lockdownController: ZenLockdownController,
    getSettings: GetSettingsUseCase,
    getBlockedApps: GetBlockedAppsUseCase,
    checkAccessibilityPermission: CheckAccessibilityPermissionUseCase,
) : ViewModel() {

    private val clearHistoryConfirmationVisible = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        getSettings(),
        getBlockedApps.enabledPackages(),
        checkAccessibilityPermission(),
        clearHistoryConfirmationVisible,
    ) { settings, blockedPackages, accessibilityEnabled, confirming ->
        SettingsUiState(
            isLoading = false,
            settings = settings,
            blockedAppCount = blockedPackages.size,
            accessibilityEnabled = accessibilityEnabled,
            lockdownCapability = lockdownController.capability(),
            showClearHistoryConfirmation = confirming,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun setDefaultDuration(minutes: Int) = update { updateSettings.setDefaultDuration(minutes) }

    fun setConfirmStart(enabled: Boolean) = update { updateSettings.setConfirmStart(enabled) }

    fun setCompletionNotification(enabled: Boolean) =
        update { updateSettings.setCompletionNotification(enabled) }

    fun setPureBlackZenScreen(enabled: Boolean) =
        update { updateSettings.setPureBlackZenScreen(enabled) }

    fun setShowClock(enabled: Boolean) = update { updateSettings.setShowClock(enabled) }

    fun setShowDate(enabled: Boolean) = update { updateSettings.setShowDate(enabled) }

    fun setUse24HourClock(enabled: Boolean) = update { updateSettings.setUse24HourClock(enabled) }

    fun setShowCallButton(enabled: Boolean) = update { updateSettings.setShowCallButton(enabled) }

    fun setStrictMode(enabled: Boolean) = update { updateSettings.setStrictMode(enabled) }

    fun onClearHistoryRequested() {
        clearHistoryConfirmationVisible.value = true
    }

    fun onClearHistoryDismissed() {
        clearHistoryConfirmationVisible.value = false
    }

    fun onClearHistoryConfirmed() {
        clearHistoryConfirmationVisible.value = false
        update { clearHistory() }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
