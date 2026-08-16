package com.zenmode.app.domain.logic

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.model.TimerSnapshot
import com.zenmode.app.domain.model.ZenSession
import javax.inject.Inject

/**
 * Derives the countdown from stored timestamps (specification §8).
 *
 * ```
 * remainingSeconds = max(0, scheduledEndAt - now)
 * ```
 *
 * Nothing here holds state. The session row carries `startedAt` and the planned
 * duration; everything else is arithmetic against [ZenClock]. That is what lets
 * the countdown survive activity recreation, rotation, process death and a
 * service restart without drifting a second.
 */
class ZenTimer @Inject constructor(
    private val clock: ZenClock,
) {

    fun snapshot(session: ZenSession?): TimerSnapshot = snapshotAt(session, clock.nowMillis())

    fun snapshotAt(session: ZenSession?, now: Long): TimerSnapshot {
        if (session == null) return TimerSnapshot.Idle
        return TimerSnapshot(
            isRunning = true,
            remainingSeconds = session.remainingSecondsAt(now),
            elapsedSeconds = session.elapsedSecondsAt(now),
            plannedDurationSeconds = session.plannedDurationSeconds,
            progress = session.progressAt(now),
            isExpired = session.isExpiredAt(now),
        )
    }

    fun remainingSeconds(session: ZenSession): Long =
        session.remainingSecondsAt(clock.nowMillis())

    fun isExpired(session: ZenSession): Boolean = session.isExpiredAt(clock.nowMillis())

    /**
     * How long the session actually ran, for the record written when it ends.
     *
     * Capped at the planned duration: if the device slept through the end of the
     * session and the completion is processed late, the user still focused for
     * the time they asked for, not the extra minutes the alarm was delayed by.
     */
    fun actualDurationSeconds(session: ZenSession, endedAt: Long): Long =
        session.elapsedSecondsAt(endedAt).coerceAtMost(session.plannedDurationSeconds)
}
