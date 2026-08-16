package com.zenmode.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One Zen session.
 *
 * The remaining time is never stored: it is always derived from [startedAt] and
 * [plannedDurationSeconds] against the current wall clock (specification §8), so
 * the countdown survives activity recreation, process death and service
 * restarts without drifting.
 *
 * All timestamps are epoch milliseconds; all durations are seconds.
 */
data class ZenSession(
    val id: Long = NO_ID,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long = 0L,
    val status: SessionStatus,
    val blockedAppCount: Int = 0,
) {
    /** When the timer is due to reach zero. */
    val scheduledEndAt: Long
        get() = startedAt + plannedDurationSeconds * MILLIS_PER_SECOND

    /** Seconds left at [now], floored at zero and capped at the planned duration. */
    fun remainingSecondsAt(now: Long): Long {
        val remainingMillis = scheduledEndAt - now
        if (remainingMillis <= 0L) return 0L
        // Round up so a session started at T shows its full duration at T, and the
        // display only reaches 0 when the session is genuinely over.
        val remainingSeconds = (remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        // If the system clock jumps backwards mid-session the raw arithmetic can
        // exceed the duration the user asked for; never show more than that.
        return remainingSeconds.coerceAtMost(plannedDurationSeconds)
    }

    /** Seconds elapsed since the session started, floored at zero. */
    fun elapsedSecondsAt(now: Long): Long =
        ((now - startedAt) / MILLIS_PER_SECOND).coerceAtLeast(0L)

    /** True once the planned duration has run out. */
    fun isExpiredAt(now: Long): Boolean = now >= scheduledEndAt

    /** Progress through the session in the range 0f..1f. */
    fun progressAt(now: Long): Float {
        if (plannedDurationSeconds <= 0L) return 1f
        val elapsed = elapsedSecondsAt(now).toFloat()
        return (elapsed / plannedDurationSeconds.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * The calendar day this session belongs to: the day it ended, falling back
     * to the day it started while it is still running.
     *
     * Streaks, statistics and history all group by this one definition, so a
     * session that runs past midnight lands in the same bucket everywhere.
     */
    fun dayAt(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(endedAt ?: startedAt).atZone(zone).toLocalDate()

    companion object {
        const val NO_ID: Long = 0L
        const val MILLIS_PER_SECOND: Long = 1_000L
    }
}
