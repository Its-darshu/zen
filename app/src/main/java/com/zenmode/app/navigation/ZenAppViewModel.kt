package com.zenmode.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app opens, once it knows enough to decide. */
sealed interface AppStartState {
    data object Loading : AppStartState
    data class Ready(val startRoute: String) : AppStartState
}

/**
 * Picks the first screen.
 *
 * A session that is still running wins over everything else: reopening the app
 * mid-session puts the user straight back on the Zen screen, which is also how
 * the app recovers after being killed (specification §8, §27).
 */
@HiltViewModel
class ZenAppViewModel @Inject constructor(
    private val getActiveSession: GetActiveSessionUseCase,
    private val getSettings: GetSettingsUseCase,
) : ViewModel() {

    private val _startState = MutableStateFlow<AppStartState>(AppStartState.Loading)
    val startState: StateFlow<AppStartState> = _startState.asStateFlow()

    /**
     * Whether Android should be asked to hold the device right now.
     *
     * True only while a session is genuinely running and the user has turned
     * strict mode on. The activity turns this into a lock task call; how much
     * that achieves is up to the platform.
     */
    val shouldHoldDevice: StateFlow<Boolean> = combine(
        getActiveSession(),
        getSettings(),
    ) { session, settings ->
        session != null && settings.strictMode
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false,
    )

    /** True while a session is running, so the activity can show over the lock screen. */
    val sessionActive: StateFlow<Boolean> = getActiveSession()
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

    init {
        viewModelScope.launch {
            val hasActiveSession = getActiveSession.current() != null
            val onboardingCompleted = getSettings.current().onboardingCompleted
            _startState.value = AppStartState.Ready(
                when {
                    hasActiveSession -> ZenRoute.ZEN
                    !onboardingCompleted -> ZenRoute.ONBOARDING
                    else -> ZenRoute.HOME
                },
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
