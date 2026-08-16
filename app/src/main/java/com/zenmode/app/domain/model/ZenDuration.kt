package com.zenmode.app.domain.model

/**
 * The rules a session duration has to satisfy (specification §14).
 */
object ZenDuration {

    /** Presets offered on the timer screen. */
    val PRESET_MINUTES: List<Int> = listOf(15, 25, 45, 60, 90, 120)

    /** Short enough to try the app out once, long enough to mean something. */
    const val MIN_MINUTES: Int = 1

    /** Half a day. Beyond this a "focus session" stops being one. */
    const val MAX_MINUTES: Int = 12 * 60

    const val MAX_CUSTOM_HOURS: Int = MAX_MINUTES / 60

    fun validate(minutes: Int): DurationValidation = when {
        minutes <= 0 -> DurationValidation.NotPositive
        minutes < MIN_MINUTES -> DurationValidation.TooShort(MIN_MINUTES)
        minutes > MAX_MINUTES -> DurationValidation.TooLong(MAX_MINUTES)
        else -> DurationValidation.Valid
    }

    fun minutesToSeconds(minutes: Int): Long = minutes.toLong() * 60L
}

/** Outcome of checking a requested duration. */
sealed interface DurationValidation {

    data object Valid : DurationValidation

    /** Zero or negative: not a duration at all. */
    data object NotPositive : DurationValidation

    data class TooShort(val minMinutes: Int) : DurationValidation

    data class TooLong(val maxMinutes: Int) : DurationValidation

    val isValid: Boolean get() = this is Valid
}
