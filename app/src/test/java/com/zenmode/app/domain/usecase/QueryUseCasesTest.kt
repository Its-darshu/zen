package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.logic.SessionHistoryGrouper
import com.zenmode.app.domain.logic.StatisticsCalculator
import com.zenmode.app.domain.logic.StreakCalculator
import com.zenmode.app.domain.model.RelativeDay
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.testing.FakeSessionRepository
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.SessionStore
import com.zenmode.app.testing.completedSessionEndingAt
import com.zenmode.app.testing.zenSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The read-side use cases: history, statistics and streaks. */
class QueryUseCasesTest {

    private val today = LocalDate.of(2026, 8, 16)
    private val clock = FakeZenClock()
    private val store = SessionStore()
    private val sessionRepository = FakeSessionRepository(store)

    private lateinit var getHistory: GetSessionHistoryUseCase
    private lateinit var getStatistics: GetStatisticsUseCase
    private lateinit var getStreak: GetStreakUseCase

    @Before
    fun setUp() {
        clock.setTo(today, LocalTime.of(18, 0))
        getHistory = GetSessionHistoryUseCase(sessionRepository, SessionHistoryGrouper(clock))
        getStatistics = GetStatisticsUseCase(sessionRepository, StatisticsCalculator(clock))
        getStreak = GetStreakUseCase(sessionRepository, StreakCalculator(clock))
    }

    private fun seedHistory() {
        store.seed(
            completedSessionEndingAt(clock.millisAt(today, LocalTime.of(11, 0)), 3_600L),
            zenSession(
                startedAt = clock.millisAt(today, LocalTime.of(14, 20)),
                endedAt = clock.millisAt(today, LocalTime.of(14, 31)),
                plannedDurationSeconds = 1_500L,
                actualDurationSeconds = 660L,
                status = SessionStatus.CANCELLED,
            ),
            completedSessionEndingAt(clock.millisAt(today.minusDays(1), LocalTime.of(19, 55)), 2_700L),
        )
    }

    @Test
    fun `history with no sessions is empty`() = runTest {
        assertTrue(getHistory().first().isEmpty())
    }

    @Test
    fun `history is grouped by day with today first`() = runTest {
        seedHistory()

        val groups = getHistory().first()

        assertEquals(2, groups.size)
        assertEquals(RelativeDay.TODAY, groups[0].relativeDay)
        assertEquals(2, groups[0].sessions.size)
        assertEquals(RelativeDay.YESTERDAY, groups[1].relativeDay)
    }

    @Test
    fun `history filters to one outcome`() = runTest {
        seedHistory()

        val completed = getHistory(SessionFilter.COMPLETED).first()
        val cancelled = getHistory(SessionFilter.CANCELLED).first()

        assertEquals(2, completed.sumOf { it.sessions.size })
        assertTrue(completed.all { group -> group.sessions.all { it.status == SessionStatus.COMPLETED } })
        assertEquals(1, cancelled.sumOf { it.sessions.size })
        assertEquals(RelativeDay.TODAY, cancelled.single().relativeDay)
    }

    @Test
    fun `statistics with no sessions are all zero`() = runTest {
        val stats = getStatistics().first()

        assertEquals(0, stats.allTime.sessionCount)
        assertEquals(0L, stats.allTime.totalFocusSeconds)
        assertEquals(0L, stats.allTime.averageSessionSeconds)
    }

    @Test
    fun `statistics count completed sessions and ignore the cancelled one`() = runTest {
        seedHistory()

        val stats = getStatistics().first()

        assertEquals(1, stats.today.sessionCount)
        assertEquals(3_600L, stats.today.totalFocusSeconds)
        assertEquals(2, stats.allTime.sessionCount)
        assertEquals(6_300L, stats.allTime.totalFocusSeconds)
        assertEquals(3_150L, stats.allTime.averageSessionSeconds)
    }

    @Test
    fun `statistics update when a session is added`() = runTest {
        assertEquals(0, getStatistics().first().allTime.sessionCount)

        store.seed(completedSessionEndingAt(clock.millisAt(today, LocalTime.NOON), 1_200L))

        assertEquals(1, getStatistics().first().allTime.sessionCount)
        assertEquals(1_200L, getStatistics().first().today.totalFocusSeconds)
    }

    @Test
    fun `the streak is derived from the same history`() = runTest {
        seedHistory()

        val streak = getStreak().first()

        assertEquals(2, streak.currentStreak)
        assertEquals(2, streak.bestStreak)
        assertEquals(today, streak.lastCompletedDate)
    }

    @Test
    fun `clearing history resets statistics and streaks together`() = runTest {
        seedHistory()

        sessionRepository.clearHistory()

        assertTrue(getHistory().first().isEmpty())
        assertEquals(0, getStatistics().first().allTime.sessionCount)
        assertEquals(0, getStreak().first().currentStreak)
        assertEquals(0, getStreak().first().bestStreak)
    }
}
