package com.zenmode.app.domain.logic

import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.completedSessionEndingAt
import com.zenmode.app.testing.zenSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Streak rules from specification §18. */
class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 8, 16)
    private val clock = FakeZenClock()
    private lateinit var calculator: StreakCalculator

    @Before
    fun setUp() {
        clock.setTo(today, LocalTime.of(18, 0))
        calculator = StreakCalculator(clock)
    }

    /** A completed session finishing at noon on [date]. */
    private fun completedOn(date: LocalDate, durationSeconds: Long = 1_500L) =
        completedSessionEndingAt(clock.millisAt(date, LocalTime.NOON), durationSeconds)

    @Test
    fun `no sessions means no streak`() {
        val streak = calculator.calculate(emptyList())

        assertEquals(0, streak.currentStreak)
        assertEquals(0, streak.bestStreak)
        assertNull(streak.lastCompletedDate)
    }

    @Test
    fun `the first completed day is a streak of one`() {
        val streak = calculator.calculate(listOf(completedOn(today)))

        assertEquals(1, streak.currentStreak)
        assertEquals(1, streak.bestStreak)
        assertEquals(today, streak.lastCompletedDate)
    }

    @Test
    fun `consecutive days build the streak`() {
        val sessions = listOf(
            completedOn(today.minusDays(2)),
            completedOn(today.minusDays(1)),
            completedOn(today),
        )

        assertEquals(3, calculator.calculate(sessions).currentStreak)
    }

    @Test
    fun `several sessions on the same day still count as one day`() {
        val sessions = listOf(
            completedOn(today.minusDays(1)),
            completedSessionEndingAt(clock.millisAt(today, LocalTime.of(9, 0))),
            completedSessionEndingAt(clock.millisAt(today, LocalTime.of(14, 0))),
            completedSessionEndingAt(clock.millisAt(today, LocalTime.of(17, 0))),
        )

        assertEquals(2, calculator.calculate(sessions).currentStreak)
    }

    @Test
    fun `a missed day resets the streak`() {
        val sessions = listOf(
            completedOn(today.minusDays(4)),
            completedOn(today.minusDays(3)),
            // nothing on day -2 or -1
            completedOn(today),
        )

        val streak = calculator.calculate(sessions)

        assertEquals(1, streak.currentStreak)
        assertEquals(2, streak.bestStreak)
    }

    @Test
    fun `today without a session yet keeps yesterday's streak alive`() {
        val sessions = listOf(
            completedOn(today.minusDays(2)),
            completedOn(today.minusDays(1)),
        )

        val streak = calculator.calculate(sessions)

        assertEquals(2, streak.currentStreak)
        assertTrue(streak.isAtRiskOn(today))
    }

    @Test
    fun `a whole day missed ends the streak`() {
        val sessions = listOf(
            completedOn(today.minusDays(3)),
            completedOn(today.minusDays(2)),
        )

        assertEquals(0, calculator.calculate(sessions).currentStreak)
        assertEquals(2, calculator.calculate(sessions).bestStreak)
    }

    @Test
    fun `the best streak survives a later reset`() {
        val sessions = listOf(
            completedOn(today.minusDays(10)),
            completedOn(today.minusDays(9)),
            completedOn(today.minusDays(8)),
            completedOn(today.minusDays(7)),
            completedOn(today.minusDays(6)),
            completedOn(today),
        )

        val streak = calculator.calculate(sessions)

        assertEquals(1, streak.currentStreak)
        assertEquals(5, streak.bestStreak)
    }

    @Test
    fun `the current streak can also be the best streak`() {
        val sessions = (0..3).map { completedOn(today.minusDays(it.toLong())) }

        val streak = calculator.calculate(sessions)

        assertEquals(4, streak.currentStreak)
        assertEquals(4, streak.bestStreak)
    }

    @Test
    fun `cancelled sessions never count`() {
        val sessions = listOf(
            completedOn(today.minusDays(1)),
            zenSession(
                startedAt = clock.millisAt(today, LocalTime.of(10, 0)),
                endedAt = clock.millisAt(today, LocalTime.of(10, 5)),
                status = SessionStatus.CANCELLED,
            ),
        )

        val streak = calculator.calculate(sessions)

        // Yesterday keeps the streak alive; today's cancellation adds nothing.
        assertEquals(1, streak.currentStreak)
        assertEquals(today.minusDays(1), streak.lastCompletedDate)
    }

    @Test
    fun `a running session does not count until it is completed`() {
        val sessions = listOf(
            zenSession(
                startedAt = clock.millisAt(today, LocalTime.of(17, 0)),
                endedAt = null,
                status = SessionStatus.ACTIVE,
            ),
        )

        assertEquals(0, calculator.calculate(sessions).currentStreak)
    }

    @Test
    fun `the order sessions arrive in does not matter`() {
        val shuffled = listOf(
            completedOn(today),
            completedOn(today.minusDays(2)),
            completedOn(today.minusDays(1)),
        )

        assertEquals(3, calculator.calculate(shuffled).currentStreak)
    }

    @Test
    fun `a streak completed today is not at risk`() {
        val streak = calculator.calculate(listOf(completedOn(today)))

        assertFalse(streak.isAtRiskOn(today))
    }

    @Test
    fun `a session finishing after midnight counts for the new day`() {
        val sessions = listOf(
            completedOn(today.minusDays(1)),
            // Started at 23:40 yesterday, finished 00:25 today.
            completedSessionEndingAt(
                endedAt = clock.millisAt(today, LocalTime.of(0, 25)),
                durationSeconds = 2_700L,
            ),
        )

        val streak = calculator.calculate(sessions)

        assertEquals(2, streak.currentStreak)
        assertEquals(today, streak.lastCompletedDate)
    }
}
