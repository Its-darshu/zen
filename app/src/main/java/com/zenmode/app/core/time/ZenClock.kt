package com.zenmode.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only way the app is allowed to ask what time it is.
 *
 * Every time-dependent rule — the countdown, streaks, "today" — goes through
 * this interface, so all of it can be tested deterministically instead of
 * against the machine that happens to be running the tests.
 *
 * The zone is read per call rather than captured once: the user can change
 * time zone mid-session, and "today" has to follow them.
 */
interface ZenClock {

    /** Wall-clock time as epoch milliseconds. */
    fun nowMillis(): Long

    /** The zone that decides what counts as a calendar day. */
    fun zone(): ZoneId

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()

    fun today(): LocalDate = toLocalDate(nowMillis())

    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone()).toInstant().toEpochMilli()
}
