package com.zenmode.app.domain.logic

import com.zenmode.app.domain.model.SessionStatus

/**
 * The Zen session state machine (specification §9).
 *
 * ```
 *  IDLE ──► STARTING ──► ACTIVE ──► COMPLETING ──► COMPLETED ──┐
 *             │            │                                    │
 *             │            └────────────► CANCELLED ────────────┤
 *             └──► IDLE (start aborted)                         │
 *                                                               ▼
 *                                                             IDLE
 * ```
 *
 * `ACTIVE ──► PAUSED ──► ACTIVE` exists in the model but is switched off for
 * the MVP: a Zen session is meant to be continuous, so [pauseSupported]
 * defaults to `false` and any transition through [SessionStatus.PAUSED] is
 * rejected.
 *
 * A session never jumps straight from ACTIVE to COMPLETED. It passes through
 * COMPLETING, which is the window where the session is being wound down —
 * blocking switched off, record written, statistics refreshed — so a process
 * death halfway through is distinguishable from a finished session.
 */
class ZenSessionStateMachine(
    private val pauseSupported: Boolean = PAUSE_SUPPORTED_IN_MVP,
) {

    /** Every state reachable in one step from [state]. */
    fun allowedTransitionsFrom(state: SessionStatus): Set<SessionStatus> {
        val transitions = when (state) {
            SessionStatus.IDLE -> setOf(SessionStatus.STARTING)
            // A start that fails validation or loses the race for the active slot
            // falls back to IDLE.
            SessionStatus.STARTING -> setOf(SessionStatus.ACTIVE, SessionStatus.IDLE)
            SessionStatus.ACTIVE -> setOf(
                SessionStatus.COMPLETING,
                SessionStatus.CANCELLED,
                SessionStatus.PAUSED,
            )
            SessionStatus.PAUSED -> setOf(
                SessionStatus.ACTIVE,
                SessionStatus.COMPLETING,
                SessionStatus.CANCELLED,
            )
            SessionStatus.COMPLETING -> setOf(SessionStatus.COMPLETED)
            // Terminal states are acknowledged and the app returns to rest.
            SessionStatus.COMPLETED, SessionStatus.CANCELLED -> setOf(SessionStatus.IDLE)
        }
        return if (pauseSupported) transitions else transitions - SessionStatus.PAUSED
    }

    fun canTransition(from: SessionStatus, to: SessionStatus): Boolean {
        if (!pauseSupported && (from == SessionStatus.PAUSED || to == SessionStatus.PAUSED)) {
            return false
        }
        return to in allowedTransitionsFrom(from)
    }

    /**
     * @return [to] when the move is legal, otherwise `null`. Callers that treat
     *   an illegal move as a bug should use [transition].
     */
    fun transitionOrNull(from: SessionStatus, to: SessionStatus): SessionStatus? =
        if (canTransition(from, to)) to else null

    /**
     * @throws IllegalStateException when the move is not part of the machine.
     */
    fun transition(from: SessionStatus, to: SessionStatus): SessionStatus =
        transitionOrNull(from, to)
            ?: error("Illegal Zen session transition: $from → $to")

    companion object {
        /** Pausing is deliberately not offered in the MVP (specification §9). */
        const val PAUSE_SUPPORTED_IN_MVP = false
    }
}
