package com.zenmode.app.domain.logic

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.model.PeriodStats
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.model.ZenStatistics
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Focus statistics (specification §17).
 *
 * Only completed sessions count. Cancelled and still-running sessions are
 * excluded everywhere, so the totals only ever describe focus the user actually
 * finished.
 *
 * Sessions are bucketed by [ZenSession.dayAt] — the same day definition streaks
 * and history use — and the week starts on Monday (ISO-8601) so the boundary
 * does not shift with the device locale.
 */
class StatisticsCalculator @Inject constructor(
    private val clock: ZenClock,
) {

    fun calculate(sessions: List<ZenSession>): ZenStatistics =
        calculateOn(sessions, clock.today())

    fun calculateOn(sessions: List<ZenSession>, today: LocalDate): ZenStatistics {
        val zone = clock.zone()
        val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return ZenStatistics.Empty

        val startOfWeek = today.with(DayOfWeek.MONDAY)
        val startOfMonth = today.withDayOfMonth(1)
        val dated = completed.map { session -> session.dayAt(zone) to session }

        return ZenStatistics(
            today = dated.statsWhere { it == today },
            thisWeek = dated.statsWhere { it >= startOfWeek && it <= today },
            thisMonth = dated.statsWhere { it >= startOfMonth && it <= today },
            allTime = dated.statsWhere { true },
        )
    }

    private fun List<Pair<LocalDate, ZenSession>>.statsWhere(
        predicate: (LocalDate) -> Boolean,
    ): PeriodStats {
        var count = 0
        var totalSeconds = 0L
        forEach { (day, session) ->
            if (predicate(day)) {
                count++
                totalSeconds += session.actualDurationSeconds
            }
        }
        return PeriodStats(sessionCount = count, totalFocusSeconds = totalSeconds)
    }
}
