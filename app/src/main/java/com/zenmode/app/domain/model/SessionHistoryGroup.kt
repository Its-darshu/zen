package com.zenmode.app.domain.model

import java.time.LocalDate

/** How a history day heading should read (specification §19). */
enum class RelativeDay {
    TODAY,
    YESTERDAY,
    EARLIER,
}

/**
 * One day of session history. The UI turns [date] and [relativeDay] into a
 * heading; the domain decides only which sessions belong together.
 */
data class SessionHistoryGroup(
    val date: LocalDate,
    val relativeDay: RelativeDay,
    val sessions: List<ZenSession>,
)
