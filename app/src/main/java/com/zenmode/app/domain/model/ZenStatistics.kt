package com.zenmode.app.domain.model

/**
 * Focus numbers for one period (specification §17). Built from completed
 * sessions only — a cancelled session is not focus time.
 */
data class PeriodStats(
    val sessionCount: Int = 0,
    val totalFocusSeconds: Long = 0L,
) {
    /** Mean length of a completed session, rounded down. Zero when there are none. */
    val averageSessionSeconds: Long
        get() = if (sessionCount == 0) 0L else totalFocusSeconds / sessionCount

    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        val Empty = PeriodStats()
    }
}

/** The statistics screen's data, sliced by period. */
data class ZenStatistics(
    val today: PeriodStats = PeriodStats.Empty,
    val thisWeek: PeriodStats = PeriodStats.Empty,
    val thisMonth: PeriodStats = PeriodStats.Empty,
    val allTime: PeriodStats = PeriodStats.Empty,
) {
    companion object {
        val Empty = ZenStatistics()
    }
}

/** The period selector on the statistics screen. */
enum class StatisticsPeriod {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    ALL_TIME,
}

fun ZenStatistics.forPeriod(period: StatisticsPeriod): PeriodStats = when (period) {
    StatisticsPeriod.TODAY -> today
    StatisticsPeriod.THIS_WEEK -> thisWeek
    StatisticsPeriod.THIS_MONTH -> thisMonth
    StatisticsPeriod.ALL_TIME -> allTime
}
