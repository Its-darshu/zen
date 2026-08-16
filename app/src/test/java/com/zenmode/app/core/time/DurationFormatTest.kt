package com.zenmode.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DurationFormatTest {

    private val utc = ZoneId.of("UTC")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun `the timer readout keeps a fixed width`() {
        assertEquals("00:00:00", DurationFormat.timer(0))
        assertEquals("00:00:59", DurationFormat.timer(59))
        assertEquals("00:01:00", DurationFormat.timer(60))
        assertEquals("01:24:37", DurationFormat.timer(5_077))
        assertEquals("02:00:00", DurationFormat.timer(7_200))
    }

    @Test
    fun `a negative timer never renders as negative`() {
        assertEquals("00:00:00", DurationFormat.timer(-5))
    }

    @Test
    fun `totals read the way the specification shows them`() {
        assertEquals("38h 42m", DurationFormat.total(139_320))
        assertEquals("42m", DurationFormat.total(2_520))
        assertEquals("0m", DurationFormat.total(0))
    }

    @Test
    fun `session lengths read naturally`() {
        assertEquals("49 min", DurationFormat.sessionLength(2_940))
        assertEquals("1h 30m", DurationFormat.sessionLength(5_400))
        assertEquals("2h", DurationFormat.sessionLength(7_200))
        assertEquals("0 min", DurationFormat.sessionLength(0))
        assertEquals("< 1 min", DurationFormat.sessionLength(30))
    }

    @Test
    fun `duration labels suit buttons`() {
        assertEquals("25 MIN", DurationFormat.durationLabel(25))
        assertEquals("2 HR", DurationFormat.durationLabel(120))
        assertEquals("1 HR 30 MIN", DurationFormat.durationLabel(90))
    }

    @Test
    fun `the clock follows the 24-hour setting`() {
        val evening = millis(2026, 8, 16, 17, 42)

        assertEquals("17:42", DurationFormat.clock(evening, utc, use24HourClock = true))
        assertEquals("5:42 PM", DurationFormat.clock(evening, utc, use24HourClock = false))
    }

    @Test
    fun `the clock respects the zone it is given`() {
        val noonUtc = millis(2026, 8, 16, 12, 0)

        assertEquals(
            "17:30",
            DurationFormat.clock(noonUtc, ZoneId.of("Asia/Kolkata"), use24HourClock = true),
        )
    }

    @Test
    fun `dates render as the Zen screen shows them`() {
        assertEquals("AUG 16, 2026", DurationFormat.longDate(LocalDate.of(2026, 8, 16)))
        assertEquals("AUG 16, 2026", DurationFormat.longDate(millis(2026, 8, 16, 9, 0), utc))
        assertEquals("AUG 13", DurationFormat.shortDate(LocalDate.of(2026, 8, 13)))
    }

    @Test
    fun `a time range shows both ends`() {
        val start = millis(2026, 8, 16, 17, 0)
        val end = millis(2026, 8, 16, 18, 0)

        assertEquals("17:00 → 18:00", DurationFormat.timeRange(start, end, utc, true))
    }

    @Test
    fun `a running session has no end time yet`() {
        val start = millis(2026, 8, 16, 17, 0)

        assertEquals("17:00 → …", DurationFormat.timeRange(start, null, utc, true))
    }
}
