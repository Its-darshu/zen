package com.zenmode.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.ZenDuration
import com.zenmode.app.domain.model.ZenStatistics
import com.zenmode.app.domain.model.ZenStreak
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.domain.usecase.GetStatisticsUseCase
import com.zenmode.app.domain.usecase.GetStreakUseCase
import com.zenmode.app.system.ZenModeManager
import com.zenmode.app.system.ZenSetupStatus
import com.zenmode.app.system.ZenSetupStatusProvider
import com.zenmode.app.system.ZenStartOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot things that happen in response to a tap. */
sealed interface HomeEvent {
    data object SessionStarted : HomeEvent
    data class Error(val message: String) : HomeEvent
}

/** The parts of the home state that come straight from the domain. */
private data class HomeSnapshot(
    val streak: ZenStreak,
    val statistics: ZenStatistics,
    val setup: ZenSetupStatus,
    val hasActiveSession: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val zenModeManager: ZenModeManager,
    private val getSettings: GetSettingsUseCase,
    private val setupStatusProvider: ZenSetupStatusProvider,
    getStreak: GetStreakUseCase,
    getStatistics: GetStatisticsUseCase,
    getActiveSession: GetActiveSessionUseCase,
) : ViewModel() {

    private val selectedMinutes = MutableStateFlow<Int?>(null)
    private val startConfirmationVisible = MutableStateFlow(false)

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

    private val snapshot: Flow<HomeSnapshot> = combine(
        getStreak(),
        getStatistics(),
        setupStatusProvider.observe(),
        getActiveSession(),
    ) { streak, statistics, setup, activeSession ->
        HomeSnapshot(
            streak = streak,
            statistics = statistics,
            setup = setup,
            hasActiveSession = activeSession != null,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        snapshot,
        getSettings(),
        selectedMinutes,
        startConfirmationVisible,
    ) { snapshot, settings, chosenMinutes, confirming ->
        HomeUiState(
            isLoading = false,
            currentStreak = snapshot.streak.currentStreak,
            totalFocusSeconds = snapshot.statistics.allTime.totalFocusSeconds,
            completedSessions = snapshot.statistics.allTime.sessionCount,
            quickPresetMinutes = quickPresets(settings.defaultDurationMinutes),
            selectedMinutes = chosenMinutes ?: settings.defaultDurationMinutes,
            blockedAppCount = snapshot.setup.blockedAppCount,
            accessibilityEnabled = snapshot.setup.accessibilityEnabled,
            exactAlarmsAvailable = snapshot.setup.exactAlarmsAvailable,
            notificationsEnabled = snapshot.setup.notificationsEnabled,
            hasActiveSession = snapshot.hasActiveSession,
            showStartConfirmation = confirming,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    fun selectDuration(minutes: Int) {
        selectedMinutes.value = minutes
    }

    /**
     * Re-reads the platform states Android does not broadcast, so returning
     * from its settings screens shows the truth straight away.
     */
    fun refreshSetupStatus() {
        setupStatusProvider.refresh()
    }

    /** Start pressed: confirm first when the user has asked us to. */
    fun onStartRequested() {
        viewModelScope.launch {
            if (getSettings.current().confirmStart) {
                startConfirmationVisible.value = true
            } else {
                start()
            }
        }
    }

    fun onStartConfirmed() {
        startConfirmationVisible.value = false
        viewModelScope.launch { start() }
    }

    fun onStartDismissed() {
        startConfirmationVisible.value = false
    }

    private suspend fun start() {
        val minutes = selectedMinutes.value ?: getSettings.current().defaultDurationMinutes
        // Everything that touches system state goes through the manager, so the
        // service, the alarm and the database can never disagree.
        val event = when (val outcome = zenModeManager.startSession(minutes)) {
            is ZenStartOutcome.Started -> HomeEvent.SessionStarted
            // Already running: the Zen screen is where the user should be anyway.
            is ZenStartOutcome.AlreadyActive -> HomeEvent.SessionStarted
            is ZenStartOutcome.Rejected -> HomeEvent.Error(outcome.reason)
        }
        events.send(event)
    }

    /** The user's own default first, then the nearest standard presets. */
    private fun quickPresets(defaultMinutes: Int): List<Int> =
        (listOf(defaultMinutes) + ZenDuration.PRESET_MINUTES)
            .distinct()
            .take(HomeUiState.DEFAULT_QUICK_PRESETS.size)
            .sorted()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
