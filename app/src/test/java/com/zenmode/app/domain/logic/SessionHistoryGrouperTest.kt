package com.zenmode.app.domain.logic

import com.zenmode.app.domain.model.RelativeDay
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.completedSessionEndingAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SessionHistoryGrouperTest {

    private val today = LocalDate.of(2026, 8, 16)
    private val clock = FakeZenClock()
    private lateinit var grouper: SessionHistoryGrouper

    @Before
    fun setUp() {
        clock.setTo(today, LocalTime.of(18, 0))
        grouper = SessionHistoryGrouper(clock)
    }

    private fun sessionEndingAt(date: LocalDate, time: LocalTime, id: Long = 0) =
        completedSessionEndingAt(clock.millisAt(date, time), id = id)

    @Test
    fun `an empty history has no groups`() {
        assertTrue(grouper.group(emptyList()).isEmpty())
    }

    @Test
    fun `sessions are grouped by day newest day first`() {
        val sessions = listOf(
            sessionEndingAt(today.minusDays(3), LocalTime.NOON),
            sessionEndingAt(today, LocalTime.NOON),
            sessionEndingAt(today.minusDays(1), LocalTime.NOON),
        )

        val groups = grouper.group(sessions)

        assertEquals(
            listOf(today, today.minusDays(1), today.minusDays(3)),
            groups.map { it.date },
        )
    }

    @Test
    fun `the two most recent days are labelled today and yesterday`() {
        val sessions = listOf(
            sessionEndingAt(today, LocalTime.NOON),
            sessionEndingAt(today.minusDays(1), LocalTime.NOON),
            sessionEndingAt(today.minusDays(2), LocalTime.NOON),
        )

        val groups = grouper.group(sessions)

        assertEquals(RelativeDay.TODAY, groups[0].relativeDay)
        assertEquals(RelativeDay.YESTERDAY, groups[1].relativeDay)
        assertEquals(RelativeDay.EARLIER, groups[2].relativeDay)
    }

    @Test
    fun `sessions within a day are newest first`() {
        val sessions = listOf(
            sessionEndingAt(today, LocalTime.of(9, 0), id = 1),
            sessionEndingAt(today, LocalTime.of(17, 0), id = 2),
            sessionEndingAt(today, LocalTime.of(13, 0), id = 3),
        )

        val group = grouper.group(sessions).single()

        assertEquals(listOf(2L, 3L, 1L), group.sessions.map { it.id })
    }

    @Test
    fun `every session ends up in exactly one group`() {
        val sessions = listOf(
            sessionEndingAt(today, LocalTime.of(9, 0)),
            sessionEndingAt(today, LocalTime.of(17, 0)),
            sessionEndingAt(today.minusDays(1), LocalTime.of(20, 0)),
        )

        val groups = grouper.group(sessions)

        assertEquals(2, groups.size)
        assertEquals(sessions.size, groups.sumOf { it.sessions.size })
    }
}
