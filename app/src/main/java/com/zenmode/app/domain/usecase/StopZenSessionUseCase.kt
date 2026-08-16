package com.zenmode.app.domain.usecase

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.ZenSessionStateMachine
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.ZenModeRepository
import javax.inject.Inject

sealed interface StopZenSessionResult {

    /** The user gave up on the session; it is recorded as cancelled. */
    data class Cancelled(val session: ZenSession) : StopZenSessionResult

    /**
     * The timer had already run out when stop was pressed, so the session was
     * completed instead of cancelled — the user earned it.
     */
    data class Completed(val session: ZenSession) : StopZenSessionResult

    data object NoActiveSession : StopZenSessionResult
}

/**
 * Stops a running session at the user's request (specification §10, §11).
 *
 * A cancelled session is stored with the time actually spent and never counts
 * as completed focus: it does not feed statistics or extend a streak.
 *
 * If the timer has already expired — the completion alarm was delayed, or the
 * device was asleep when it fired — stopping completes the session rather than
 * cancelling it. The user did the work; a late tap should not take it away.
 */
class StopZenSessionUseCase @Inject constructor(
    private val zenModeRepository: ZenModeRepository,
    private val completeZenSession: CompleteZenSessionUseCase,
    private val stateMachine: ZenSessionStateMachine,
    private val clock: ZenClock,
) {

    suspend operator fun invoke(): StopZenSessionResult {
        val active = zenModeRepository.getActiveSession()
            ?: return StopZenSessionResult.NoActiveSession

        val now = clock.nowMillis()
        if (active.isExpiredAt(now)) {
            return when (val result = completeZenSession()) {
                is CompleteZenSessionResult.Completed -> StopZenSessionResult.Completed(result.session)
                else -> StopZenSessionResult.NoActiveSession
            }
        }

        val cancelled = stateMachine.transition(active.status, SessionStatus.CANCELLED)
        val stopped = zenModeRepository.finishActiveSession(
            status = cancelled,
            endedAt = now,
            actualDurationSeconds = active.elapsedSecondsAt(now),
        ) ?: return StopZenSessionResult.NoActiveSession

        return StopZenSessionResult.Cancelled(stopped)
    }
}
