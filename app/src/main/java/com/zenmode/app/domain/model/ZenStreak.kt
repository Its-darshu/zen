package com.zenmode.app.domain.model

import java.time.LocalDate

/**
 * Streak state (specification §18).
 *
 * Motivational only. Device time is not a trusted source and this is not
 * treated as one: nothing about a streak gates functionality.
 */
data class ZenStreak(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCompletedDate: LocalDate? = null,
) {
    /** True when today still needs a completed session to extend the streak. */
    fun isAtRiskOn(today: LocalDate): Boolean =
        currentStreak > 0 && lastCompletedDate != null && lastCompletedDate < today

    companion object {
        val None = ZenStreak()
    }
}
