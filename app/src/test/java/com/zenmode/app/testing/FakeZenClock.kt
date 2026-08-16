package com.zenmode.app.testing

import com.zenmode.app.core.time.ZenClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A clock the test drives by hand. Fixed to UTC unless a test says otherwise,
 * so day boundaries are the same wherever the suite runs.
 */
class FakeZenClock(
    var now: Long = 0L,
    private var zone: ZoneId = ZoneId.of("UTC"),
) : ZenClock {

    override fun nowMillis(): Long = now

    override fun zone(): ZoneId = zone

    fun setZone(zone: ZoneId) {
        this.zone = zone
    }

    /** Moves the clock forward (or backward, with a negative value). */
    fun advanceSeconds(seconds: Long) {
        now += seconds * 1_000L
    }

    fun advanceMillis(millis: Long) {
        now += millis
    }

    fun setTo(dateTime: LocalDateTime) {
        now = dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    fun setTo(date: LocalDate, time: LocalTime = LocalTime.NOON) {
        setTo(LocalDateTime.of(date, time))
    }

    fun millisAt(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
}
