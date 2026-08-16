package com.zenmode.app.domain.model

import com.zenmode.app.testing.zenSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * The timestamp arithmetic the whole countdown rests on (specification §8).
 */
class ZenSessionTest {

    private val oneHour = zenSession(
        startedAt = 1_000_000L,
        plannedDurationSeconds = 3_600L,
        status = SessionStatus.ACTIVE,
    )

    @Test
    fun `the end time is derived from the start and the planned duration`() {
        assertEquals(1_000_000L + 3_600_000L, oneHour.scheduledEndAt)
    }

    @Test
    fun `at the moment it starts the full duration remains`() {
        assertEquals(3_600L, oneHour.remainingSecondsAt(oneHour.startedAt))
        assertEquals(0L, oneHour.elapsedSecondsAt(oneHour.startedAt))
        assertEquals(0f, oneHour.progressAt(oneHour.startedAt), 0.0001f)
        assertFalse(oneHour.isExpiredAt(oneHour.startedAt))
    }

    @Test
    fun `remaining time counts down with the clock`() {
        assertEquals(3_599L, oneHour.remainingSecondsAt(oneHour.startedAt + 1_000L))
        assertEquals(1_800L, oneHour.remainingSecondsAt(oneHour.startedAt + 1_800_000L))
        assertEquals(1L, oneHour.remainingSecondsAt(oneHour.scheduledEndAt - 1_000L))
    }

    @Test
    fun `a part-second is rounded up so the display only shows zero when it is over`() {
        // 1500 ms left reads as 2 seconds, 1 ms left still reads as 1 second.
        assertEquals(2L, oneHour.remainingSecondsAt(oneHour.scheduledEndAt - 1_500L))
        assertEquals(1L, oneHour.remainingSecondsAt(oneHour.scheduledEndAt - 1L))
    }

    @Test
    fun `remaining time is exactly zero at the end instant`() {
        assertEquals(0L, oneHour.remainingSecondsAt(oneHour.scheduledEndAt))
        assertTrue(oneHour.isExpiredAt(oneHour.scheduledEndAt))
    }

    @Test
    fun `remaining time never goes negative once the session is over`() {
        assertEquals(0L, oneHour.remainingSecondsAt(oneHour.scheduledEndAt + 1L))
        assertEquals(0L, oneHour.remainingSecondsAt(oneHour.scheduledEndAt + 86_400_000L))
        assertTrue(oneHour.isExpiredAt(oneHour.scheduledEndAt + 86_400_000L))
    }

    @Test
    fun `a clock jumping backwards never shows more than the planned duration`() {
        val wayBefore = oneHour.startedAt - 10 * 3_600_000L

        assertEquals(3_600L, oneHour.remainingSecondsAt(wayBefore))
        assertEquals(0L, oneHour.elapsedSecondsAt(wayBefore))
        assertEquals(0f, oneHour.progressAt(wayBefore), 0.0001f)
    }

    @Test
    fun `progress runs from zero to one and stops there`() {
        assertEquals(0.5f, oneHour.progressAt(oneHour.startedAt + 1_800_000L), 0.0001f)
        assertEquals(1f, oneHour.progressAt(oneHour.scheduledEndAt), 0.0001f)
        assertEquals(1f, oneHour.progressAt(oneHour.scheduledEndAt + 999_000L), 0.0001f)
    }

    @Test
    fun `a zero-length session is already over and never divides by zero`() {
        val empty = zenSession(startedAt = 500L, plannedDurationSeconds = 0L)

        assertEquals(0L, empty.remainingSecondsAt(500L))
        assertTrue(empty.isExpiredAt(500L))
        assertEquals(1f, empty.progressAt(500L), 0.0001f)
    }

    @Test
    fun `a session belongs to the day it ended`() {
        val zone = ZoneId.of("UTC")
        val startedLateAtNight = LocalDateTime.of(2026, 8, 16, 23, 40)
            .atZone(zone).toInstant().toEpochMilli()
        val endedAfterMidnight = LocalDateTime.of(2026, 8, 17, 0, 25)
            .atZone(zone).toInstant().toEpochMilli()

        val session = zenSession(startedAt = startedLateAtNight, endedAt = endedAfterMidnight)

        assertEquals(LocalDate.of(2026, 8, 17), session.dayAt(zone))
    }

    @Test
    fun `a running session belongs to the day it started`() {
        val zone = ZoneId.of("UTC")
        val startedAt = LocalDateTime.of(2026, 8, 16, 9, 0).atZone(zone).toInstant().toEpochMilli()

        val session = zenSession(startedAt = startedAt, endedAt = null, status = SessionStatus.ACTIVE)

        assertEquals(LocalDate.of(2026, 8, 16), session.dayAt(zone))
    }
}
