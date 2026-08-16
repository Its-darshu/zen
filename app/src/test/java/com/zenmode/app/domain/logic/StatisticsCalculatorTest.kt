package com.zenmode.app.domain.logic

import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.StatisticsPeriod
import com.zenmode.app.domain.model.ZenStatistics
import com.zenmode.app.domain.model.forPeriod
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.completedSessionEndingAt
import com.zenmode.app.testing.zenSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Statistics rules from specification §17. */
class StatisticsCalculatorTest {

    // A Sunday, so "this week" (Monday-based) and "this month" differ usefully.
    private val today = LocalDate.of(2026, 8, 16)
    private val clock = FakeZenClock()
    private lateinit var calculator: StatisticsCalculator

    @Before
    fun setUp() {
        clock.setTo(today, LocalTime.of(18, 0))
        calculator = StatisticsCalculator(clock)
    }

    private fun completedOn(date: LocalDate, durationSeconds: Long) =
        completedSessionEndingAt(clock.millisAt(date, LocalTime.NOON), durationSeconds)

    @Test
    fun `an empty history produces empty statistics`() {
        assertEquals(ZenStatistics.Empty, calculator.calculate(emptyList()))
        assertTrue(calculator.calculate(emptyList()).allTime.isEmpty)
        assertEquals(0L, calculator.calculate(emptyList()).allTime.averageSessionSeconds)
    }

    @Test
    fun `completed sessions are counted and their focus time summed`() {
        val sessions = listOf(
            completedOn(today, 1_500L),
            completedOn(today, 2_700L),
        )

        val allTime = calculator.calculate(sessions).allTime

        assertEquals(2, allTime.sessionCount)
        assertEquals(4_200L, allTime.totalFocusSeconds)
    }

    @Test
    fun `cancelled sessions are excluded from every number`() {
        val sessions = listOf(
            completedOn(today, 3_600L),
            zenSession(
                startedAt = clock.millisAt(today, LocalTime.of(9, 0)),
                endedAt = clock.millisAt(today, LocalTime.of(9, 11)),
                plannedDurationSeconds = 1_500L,
                actualDurationSeconds = 660L,
                status = SessionStatus.CANCELLED,
            ),
        )

        val stats = calculator.calculate(sessions)

        assertEquals(1, stats.today.sessionCount)
        assertEquals(3_600L, stats.today.totalFocusSeconds)
        assertEquals(1, stats.allTime.sessionCount)
    }

    @Test
    fun `a running session is not focus time yet`() {
        val sessions = listOf(
            zenSession(
                startedAt = clock.millisAt(today, LocalTime.of(17, 0)),
                endedAt = null,
                status = SessionStatus.ACTIVE,
            ),
        )

        assertEquals(ZenStatistics.Empty, calculator.calculate(sessions))
    }

    @Test
    fun `the average is the mean completed session rounded down`() {
        val sessions = listOf(
            completedOn(today, 1_000L),
            completedOn(today, 1_001L),
        )

        assertEquals(1_000L, calculator.calculate(sessions).today.averageSessionSeconds)
    }

    @Test
    fun `today only counts sessions finished today`() {
        val sessions = listOf(
            completedOn(today, 1_500L),
            completedOn(today.minusDays(1), 3_600L),
        )

        val stats = calculator.calculate(sessions)

        assertEquals(1, stats.today.sessionCount)
        assertEquals(1_500L, stats.today.totalFocusSeconds)
    }

    @Test
    fun `this week runs from Monday and excludes the day before it`() {
        // today is Sunday 16 Aug 2026; the week started Monday 10 Aug.
        val sessions = listOf(
            completedOn(today, 600L),
            completedOn(LocalDate.of(2026, 8, 10), 600L),
            completedOn(LocalDate.of(2026, 8, 9), 600L),
        )

        val stats = calculator.calculate(sessions)

        assertEquals(2, stats.thisWeek.sessionCount)
        assertEquals(1_200L, stats.thisWeek.totalFocusSeconds)
        assertEquals(3, stats.allTime.sessionCount)
    }

    @Test
    fun `this month starts on the first and excludes last month`() {
        val sessions = listOf(
            completedOn(LocalDate.of(2026, 8, 1), 600L),
            completedOn(LocalDate.of(2026, 8, 14), 600L),
            completedOn(LocalDate.of(2026, 7, 31), 600L),
        )

        val stats = calculator.calculate(sessions)

        assertEquals(2, stats.thisMonth.sessionCount)
        assertEquals(3, stats.allTime.sessionCount)
    }

    @Test
    fun `all time keeps everything however old`() {
        val sessions = listOf(
            completedOn(today.minusYears(2), 1_200L),
            completedOn(today, 1_200L),
        )

        val stats = calculator.calculate(sessions)

        assertEquals(2, stats.allTime.sessionCount)
        assertEquals(2_400L, stats.allTime.totalFocusSeconds)
        assertEquals(1, stats.today.sessionCount)
        assertEquals(1, stats.thisMonth.sessionCount)
    }

    @Test
    fun `the periods nest as today within week within month within all time`() {
        val sessions = listOf(
            completedOn(today, 600L),
            completedOn(LocalDate.of(2026, 8, 11), 600L),
            completedOn(LocalDate.of(2026, 8, 3), 600L),
            completedOn(LocalDate.of(2026, 5, 3), 600L),
        )

        val stats = calculator.calculate(sessions)

        assertEquals(1, stats.today.sessionCount)
        assertEquals(2, stats.thisWeek.sessionCount)
        assertEquals(3, stats.thisMonth.sessionCount)
        assertEquals(4, stats.allTime.sessionCount)
    }

    @Test
    fun `the period selector reads the matching bucket`() {
        val sessions = listOf(completedOn(today, 900L))

        val stats = calculator.calculate(sessions)

        assertEquals(stats.today, stats.forPeriod(StatisticsPeriod.TODAY))
        assertEquals(stats.thisWeek, stats.forPeriod(StatisticsPeriod.THIS_WEEK))
        assertEquals(stats.thisMonth, stats.forPeriod(StatisticsPeriod.THIS_MONTH))
        assertEquals(stats.allTime, stats.forPeriod(StatisticsPeriod.ALL_TIME))
    }

    @Test
    fun `a session finishing after midnight counts towards the new day`() {
        val sessions = listOf(
            completedSessionEndingAt(clock.millisAt(today, LocalTime.of(0, 10)), 1_800L),
        )

        assertEquals(1, calculator.calculate(sessions).today.sessionCount)
    }

    @Test
    fun `statistics track the recorded duration not the planned one`() {
        // A completed session records what it actually ran for.
        val sessions = listOf(
            zenSession(
                startedAt = clock.millisAt(today, LocalTime.of(10, 0)),
                endedAt = clock.millisAt(today, LocalTime.of(11, 0)),
                plannedDurationSeconds = 3_600L,
                actualDurationSeconds = 3_600L,
                status = SessionStatus.COMPLETED,
            ),
        )

        assertEquals(3_600L, calculator.calculate(sessions).today.totalFocusSeconds)
    }
}
