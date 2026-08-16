package com.zenmode.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation formatting for times and durations.
 *
 * Pure functions over `java.time` — no Android, no Compose — so every string the
 * user reads can be asserted in a plain unit test.
 */
object DurationFormat {

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    /**
     * The big Zen-screen readout: `01:24:37`.
     *
     * Always hours:minutes:seconds so the digits never reflow mid-session, which
     * would pull the eye back to a screen that is meant to be ignored.
     */
    fun timer(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / SECONDS_PER_HOUR
        val minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val remainingSeconds = safe % SECONDS_PER_MINUTE
        return "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    }

    /** A total, as on the home and statistics screens: `38h 42m`, `42m`, `0m`. */
    fun total(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / SECONDS_PER_HOUR
        val minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** One session's length: `49 min`, `1h 30m`, `< 1 min`. */
    fun sessionLength(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        if (safe in 1 until SECONDS_PER_MINUTE) return "< 1 min"
        val hours = safe / SECONDS_PER_HOUR
        val minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "$minutes min"
        }
    }

    /** A chosen duration, for buttons and confirmations: `25 MIN`, `2 HR`, `1 HR 30 MIN`. */
    fun durationLabel(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val remainder = safe % 60
        return when {
            hours > 0 && remainder > 0 -> "$hours HR $remainder MIN"
            hours > 0 -> "$hours HR"
            else -> "$safe MIN"
        }
    }

    /** The wall clock: `17:42` or `5:42 PM`. */
    fun clock(epochMillis: Long, zone: ZoneId, use24HourClock: Boolean): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val pattern = if (use24HourClock) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.US).format(time)
    }

    /** Just the time of day for history rows: `17:00`. */
    fun timeOfDay(epochMillis: Long, zone: ZoneId, use24HourClock: Boolean): String =
        clock(epochMillis, zone, use24HourClock)

    /** The Zen screen's date line: `AUG 16, 2026`. */
    fun longDate(epochMillis: Long, zone: ZoneId): String =
        longDate(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    fun longDate(date: LocalDate): String =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(date).uppercase(Locale.US)

    /** A history day heading for older days: `AUG 13`. */
    fun shortDate(date: LocalDate): String =
        DateTimeFormatter.ofPattern("MMM d", Locale.US).format(date).uppercase(Locale.US)

    /** A history row's span: `17:00 → 18:00`, or `17:00 → …` while it runs. */
    fun timeRange(
        startedAt: Long,
        endedAt: Long?,
        zone: ZoneId,
        use24HourClock: Boolean,
    ): String {
        val start = clock(startedAt, zone, use24HourClock)
        val end = endedAt?.let { clock(it, zone, use24HourClock) } ?: "…"
        return "$start → $end"
    }
}
