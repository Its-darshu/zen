package com.zenmode.app.domain.usecase

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.ZenSessionStateMachine
import com.zenmode.app.domain.model.DurationValidation
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenDuration
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.BlockedAppRepository
import com.zenmode.app.domain.repository.ZenModeRepository
import javax.inject.Inject

/** The outcome of asking to start a session. */
sealed interface StartZenSessionResult {

    data class Started(val session: ZenSession) : StartZenSessionResult

    /** A session was already running; it is returned untouched. */
    data class AlreadyActive(val session: ZenSession) : StartZenSessionResult

    data class InvalidDuration(val validation: DurationValidation) : StartZenSessionResult

    /** The session could not be stored. Rare, and always safe to retry. */
    data object Failed : StartZenSessionResult
}

/**
 * Starts a Zen session (specification §3, §8, §25).
 *
 * The rules live here, in one place:
 * - the requested duration must pass [ZenDuration] validation;
 * - only one session runs at a time — the repository enforces that atomically,
 *   and losing the race is reported as [StartZenSessionResult.AlreadyActive]
 *   rather than retried;
 * - the session is stamped with `startedAt` and its planned duration, and
 *   nothing else. The end time is always derived from those two.
 *
 * Switching blocking on and putting a foreground service up is the Android
 * layer's job; this use case only decides that a session exists.
 */
class StartZenSessionUseCase @Inject constructor(
    private val zenModeRepository: ZenModeRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val stateMachine: ZenSessionStateMachine,
    private val clock: ZenClock,
) {

    suspend operator fun invoke(durationMinutes: Int): StartZenSessionResult {
        val validation = ZenDuration.validate(durationMinutes)
        if (!validation.isValid) return StartZenSessionResult.InvalidDuration(validation)

        zenModeRepository.getActiveSession()?.let { active ->
            return StartZenSessionResult.AlreadyActive(active)
        }

        // IDLE → STARTING → ACTIVE. Asserted rather than assumed: if the machine
        // ever changes shape, this fails loudly instead of writing a bad row.
        val starting = stateMachine.transition(SessionStatus.IDLE, SessionStatus.STARTING)
        val active = stateMachine.transition(starting, SessionStatus.ACTIVE)

        val session = ZenSession(
            startedAt = clock.nowMillis(),
            plannedDurationSeconds = ZenDuration.minutesToSeconds(durationMinutes),
            status = active,
            blockedAppCount = blockedAppRepository.countEnabled(),
        )

        val started = zenModeRepository.startSession(session)
            // Another caller won the race between the check above and the insert.
            ?: return zenModeRepository.getActiveSession()
                ?.let { StartZenSessionResult.AlreadyActive(it) }
                ?: StartZenSessionResult.Failed

        return StartZenSessionResult.Started(started)
    }
}
