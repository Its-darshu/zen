package com.zenmode.app.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.DurationValidation
import com.zenmode.app.domain.model.ZenDuration
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.feature.common.describe
import com.zenmode.app.system.ZenModeManager
import com.zenmode.app.system.ZenSetupStatusProvider
import com.zenmode.app.system.ZenStartOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The timer-selection screen's state (specification §14). */
data class TimerUiState(
    val presetMinutes: List<Int> = ZenDuration.PRESET_MINUTES,
    val selectedPresetMinutes: Int? = null,
    val isCustom: Boolean = false,
    val customHours: Int = 0,
    val customMinutes: Int = 25,
    val blockedAppCount: Int = 0,
    val accessibilityEnabled: Boolean = false,
    val exactAlarmsAvailable: Boolean = true,
    val showStartConfirmation: Boolean = false,
) {
    /** The duration the user has actually chosen, in minutes. */
    val totalMinutes: Int
        get() = if (isCustom) customHours * 60 + customMinutes else selectedPresetMinutes ?: 0

    val validation: DurationValidation get() = ZenDuration.validate(totalMinutes)

    val canStart: Boolean get() = validation.isValid

    /** Shown only once the choice is actually invalid, never as a pre-emptive scold. */
    val validationMessage: String?
        get() = if (validation.isValid) null else validation.describe()

    val isSetUpForBlocking: Boolean get() = accessibilityEnabled && blockedAppCount > 0

    /** Android may run the end-of-session alarm late; the user is told so. */
    val sessionEndMayBeDelayed: Boolean get() = !exactAlarmsAvailable
}

sealed interface TimerEvent {
    data object SessionStarted : TimerEvent
    data class Error(val message: String) : TimerEvent
}

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val zenModeManager: ZenModeManager,
    private val getSettings: GetSettingsUseCase,
    private val setupStatusProvider: ZenSetupStatusProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val events = Channel<TimerEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TimerEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = getSettings.current()
            _uiState.update { state ->
                state.copy(
                    selectedPresetMinutes = settings.defaultDurationMinutes
                        .takeIf { it in ZenDuration.PRESET_MINUTES },
                    customHours = settings.defaultDurationMinutes / 60,
                    customMinutes = settings.defaultDurationMinutes % 60,
                )
            }
        }
        viewModelScope.launch {
            setupStatusProvider.observe().collect { setup ->
                _uiState.update {
                    it.copy(
                        blockedAppCount = setup.blockedAppCount,
                        accessibilityEnabled = setup.accessibilityEnabled,
                        exactAlarmsAvailable = setup.exactAlarmsAvailable,
                    )
                }
            }
        }
    }

    /** Re-reads what Android does not broadcast, after a trip to its settings. */
    fun refreshSetupStatus() {
        setupStatusProvider.refresh()
    }

    fun selectPreset(minutes: Int) {
        _uiState.update { it.copy(selectedPresetMinutes = minutes, isCustom = false) }
    }

    fun selectCustom() {
        _uiState.update { it.copy(isCustom = true) }
    }

    fun setCustomHours(hours: Int) {
        _uiState.update {
            it.copy(isCustom = true, customHours = hours.coerceIn(0, ZenDuration.MAX_CUSTOM_HOURS))
        }
    }

    fun setCustomMinutes(minutes: Int) {
        _uiState.update { it.copy(isCustom = true, customMinutes = minutes.coerceIn(0, 59)) }
    }

    fun onStartRequested() {
        val state = _uiState.value
        if (!state.canStart) {
            viewModelScope.launch { events.send(TimerEvent.Error(state.validation.describe())) }
            return
        }
        viewModelScope.launch {
            if (getSettings.current().confirmStart) {
                _uiState.update { it.copy(showStartConfirmation = true) }
            } else {
                start()
            }
        }
    }

    fun onStartConfirmed() {
        _uiState.update { it.copy(showStartConfirmation = false) }
        viewModelScope.launch { start() }
    }

    fun onStartDismissed() {
        _uiState.update { it.copy(showStartConfirmation = false) }
    }

    private suspend fun start() {
        val event = when (val outcome = zenModeManager.startSession(_uiState.value.totalMinutes)) {
            is ZenStartOutcome.Started -> TimerEvent.SessionStarted
            is ZenStartOutcome.AlreadyActive -> TimerEvent.SessionStarted
            is ZenStartOutcome.Rejected -> TimerEvent.Error(outcome.reason)
        }
        events.send(event)
    }
}
