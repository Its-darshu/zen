package com.zenmode.app.domain.usecase

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.ZenSessionStateMachine
import com.zenmode.app.domain.logic.ZenTimer
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.ZenModeRepository
import javax.inject.Inject

sealed interface CompleteZenSessionResult {

    data class Completed(val session: ZenSession) : CompleteZenSessionResult

    data object NoActiveSession : CompleteZenSessionResult

    /** The timer has not run out yet. */
    data class NotExpired(val remainingSeconds: Long) : CompleteZenSessionResult
}

/**
 * Finishes a session that has run its course (specification §8, §10).
 *
 * Whether the session is over is decided by comparing timestamps, not by
 * trusting whoever called: an alarm that fires early, a service that restarts,
 * or a stale notification action all land here and are checked the same way.
 * That makes completion idempotent — calling it twice cannot write two records.
 */
class CompleteZenSessionUseCase @Inject constructor(
    private val zenModeRepository: ZenModeRepository,
    private val stateMachine: ZenSessionStateMachine,
    private val timer: ZenTimer,
    private val clock: ZenClock,
) {

    /**
     * @param force completes a session that has not expired yet. Reserved for
     *   the app shutting a session down for a reason other than the clock; the
     *   record is still written as COMPLETED.
     */
    suspend operator fun invoke(force: Boolean = false): CompleteZenSessionResult {
        val active = zenModeRepository.getActiveSession()
            ?: return CompleteZenSessionResult.NoActiveSession

        val now = clock.nowMillis()
        if (!force && !active.isExpiredAt(now)) {
            return CompleteZenSessionResult.NotExpired(active.remainingSecondsAt(now))
        }

        // ACTIVE → COMPLETING → COMPLETED.
        val completing = stateMachine.transition(active.status, SessionStatus.COMPLETING)
        val completed = stateMachine.transition(completing, SessionStatus.COMPLETED)

        val finished = zenModeRepository.finishActiveSession(
            status = completed,
            endedAt = now,
            actualDurationSeconds = timer.actualDurationSeconds(active, now),
        ) ?: return CompleteZenSessionResult.NoActiveSession

        return CompleteZenSessionResult.Completed(finished)
    }
}
