package com.zenmode.app.feature.zen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.core.time.DurationFormat
import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.ZenTimer
import com.zenmode.app.domain.model.TimerSnapshot
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.system.ZenModeManager
import com.zenmode.app.system.ZenStopOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the Zen screen shows.
 *
 * [timer] comes from the domain [ZenTimer]; this screen has no countdown of its
 * own. The ticker below only decides *when* to re-derive it.
 */
data class ZenUiState(
    val isLoading: Boolean = true,
    val hasActiveSession: Boolean = false,
    val activeSessionId: Long? = null,
    val timer: TimerSnapshot = TimerSnapshot.Idle,
    val clockText: String = "",
    val dateText: String = "",
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showCallButton: Boolean = true,
    val pureBlack: Boolean = true,
    /** The deliberate administrative escape, never advertised in the UI. */
    val showAdminEscape: Boolean = false,
) {
    val remainingText: String get() = DurationFormat.timer(timer.remainingSeconds)
}

sealed interface ZenEvent {
    /** The session ran its course. */
    data class SessionCompleted(val sessionId: Long) : ZenEvent

    /** An administrator ended the session early. */
    data object SessionCancelled : ZenEvent

    /** Nothing is running any more; leave the Zen screen. */
    data object NoSession : ZenEvent
}

@HiltViewModel
class ZenViewModel @Inject constructor(
    private val zenModeManager: ZenModeManager,
    private val getSettings: GetSettingsUseCase,
    private val zenTimer: ZenTimer,
    private val clock: ZenClock,
    getActiveSession: GetActiveSessionUseCase,
) : ViewModel() {

    private val adminEscapeVisible = MutableStateFlow(false)

    private val events = Channel<ZenEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ZenEvent> = events.receiveAsFlow()

    /**
     * Emits once a second, on the second.
     *
     * This is a redraw trigger, not the source of truth: every tick re-derives
     * the remaining time from the session's stored timestamps, so a missed or
     * late tick cannot make the countdown drift.
     */
    private val ticker: Flow<Long> = flow {
        while (true) {
            val now = clock.nowMillis()
            emit(now)
            delay(MILLIS_PER_SECOND - now % MILLIS_PER_SECOND)
        }
    }

    val uiState: StateFlow<ZenUiState> = combine(
        getActiveSession(),
        getSettings(),
        ticker,
        adminEscapeVisible,
    ) { session, settings, now, adminEscape ->
        ZenUiState(
            isLoading = false,
            hasActiveSession = session != null,
            activeSessionId = session?.id,
            timer = zenTimer.snapshotAt(session, now),
            clockText = DurationFormat.clock(now, clock.zone(), settings.use24HourClock),
            dateText = DurationFormat.longDate(now, clock.zone()),
            showClock = settings.showClock,
            showDate = settings.showDate,
            showCallButton = settings.showCallButton,
            pureBlack = settings.pureBlackZenScreen,
            showAdminEscape = adminEscape,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ZenUiState(),
    )

    init {
        // Finishes the session promptly while the screen is open. The foreground
        // service and the end-of-session alarm do the same when it is not, so
        // this is the fastest of three paths to the same guarded transition,
        // never the only one.
        viewModelScope.launch {
            uiState.collect { state ->
                val sessionId = state.activeSessionId
                if (state.hasActiveSession && state.timer.isExpired && sessionId != null) {
                    finishExpiredSession(sessionId)
                }
            }
        }
    }

    /**
     * The administrative escape.
     *
     * A started session is a commitment: there is no Stop button and Back does
     * not leave. This exists so a session can still be ended deliberately — for
     * testing, and so nobody is locked out of their own device by a mistake.
     * It is reached by a long press on the ZEN MODE label and then a
     * confirmation, so it cannot be hit by accident.
     */
    fun onAdminEscapeRequested() {
        adminEscapeVisible.value = true
    }

    fun onAdminEscapeDismissed() {
        adminEscapeVisible.value = false
    }

    fun onAdminEscapeConfirmed() {
        adminEscapeVisible.value = false
        viewModelScope.launch { endSession() }
    }

    private suspend fun endSession() {
        // The manager tears down the alarm and the service as well as writing
        // the record, so ending from here leaves nothing running behind.
        val event = when (val outcome = zenModeManager.stopSession()) {
            is ZenStopOutcome.Cancelled -> ZenEvent.SessionCancelled
            // The timer had already run out: the user earned the completion.
            is ZenStopOutcome.Completed -> ZenEvent.SessionCompleted(outcome.session.id)
            ZenStopOutcome.NoActiveSession -> ZenEvent.NoSession
        }
        events.send(event)
    }

    /**
     * The screen noticed the time is up. The session id is passed through so
     * this cannot end a session that started in the meantime.
     */
    private suspend fun finishExpiredSession(sessionId: Long) {
        when (val outcome = zenModeManager.completeIfDue(expectedSessionId = sessionId)) {
            is ZenStopOutcome.Completed -> events.send(ZenEvent.SessionCompleted(outcome.session.id))
            // Already handled elsewhere, or not actually due yet.
            else -> Unit
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
