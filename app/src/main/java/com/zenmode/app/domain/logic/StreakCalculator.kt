package com.zenmode.app.domain.logic

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.model.ZenStreak
import java.time.LocalDate
import javax.inject.Inject

/**
 * Streaks, derived from completed sessions (specification §18).
 *
 * Rules:
 * - Only [SessionStatus.COMPLETED] sessions count. Cancelling never extends a
 *   streak.
 * - Several sessions on one day count once: a streak is a run of *days*.
 * - Consecutive calendar days keep the streak; a day with nothing completed
 *   ends it.
 * - Today not having a session yet does not end the streak — the day is not
 *   over. The streak is only broken once a whole day has passed with nothing
 *   completed, so a streak anchored on yesterday is still alive.
 *
 * Nothing is cached: current and best are recomputed from the session history,
 * which is the same data history and statistics read. Stored counters could
 * drift away from the sessions they describe (clearing history, restoring a
 * backup); derived ones cannot.
 */
class StreakCalculator @Inject constructor(
    private val clock: ZenClock,
) {

    fun calculate(sessions: List<ZenSession>): ZenStreak = calculateOn(sessions, clock.today())

    fun calculateOn(sessions: List<ZenSession>, today: LocalDate): ZenStreak {
        val zone = clock.zone()
        val completedDays = sessions
            .asSequence()
            .filter { it.status == SessionStatus.COMPLETED }
            .map { it.dayAt(zone) }
            .toSortedSet()

        if (completedDays.isEmpty()) return ZenStreak.None

        return ZenStreak(
            currentStreak = currentStreak(completedDays, today),
            bestStreak = bestStreak(completedDays),
            lastCompletedDate = completedDays.last(),
        )
    }

    /**
     * Counts back from the most recent day that can still anchor a live streak:
     * today if it has a session, otherwise yesterday. Anything older means at
     * least one full day was missed and the streak has reset.
     */
    private fun currentStreak(completedDays: Set<LocalDate>, today: LocalDate): Int {
        val anchor = when {
            today in completedDays -> today
            today.minusDays(1) in completedDays -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        var day = anchor
        while (day in completedDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** The longest run of consecutive days anywhere in the history. */
    private fun bestStreak(completedDays: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        completedDays.forEach { day ->
            run = if (previous != null && previous!!.plusDays(1) == day) run + 1 else 1
            if (run > best) best = run
            previous = day
        }
        return best
    }
}
