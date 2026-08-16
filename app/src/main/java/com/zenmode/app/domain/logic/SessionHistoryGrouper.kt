package com.zenmode.app.domain.logic

import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.model.RelativeDay
import com.zenmode.app.domain.model.SessionHistoryGroup
import com.zenmode.app.domain.model.ZenSession
import java.time.LocalDate
import javax.inject.Inject

/**
 * Groups history into days, newest first, with newest sessions first inside
 * each day (specification §19).
 */
class SessionHistoryGrouper @Inject constructor(
    private val clock: ZenClock,
) {

    fun group(sessions: List<ZenSession>): List<SessionHistoryGroup> =
        groupOn(sessions, clock.today())

    fun groupOn(sessions: List<ZenSession>, today: LocalDate): List<SessionHistoryGroup> {
        if (sessions.isEmpty()) return emptyList()
        val zone = clock.zone()
        val yesterday = today.minusDays(1)

        return sessions
            .groupBy { it.dayAt(zone) }
            .toSortedMap(reverseOrder())
            .map { (date, daySessions) ->
                SessionHistoryGroup(
                    date = date,
                    relativeDay = when (date) {
                        today -> RelativeDay.TODAY
                        yesterday -> RelativeDay.YESTERDAY
                        else -> RelativeDay.EARLIER
                    },
                    sessions = daySessions.sortedByDescending { it.startedAt },
                )
            }
    }
}
