package com.zenmode.app.feature.common

import com.zenmode.app.domain.model.DurationValidation

/** Turns a validation failure into something worth showing a person. */
fun DurationValidation.describe(): String = when (this) {
    DurationValidation.Valid -> ""
    DurationValidation.NotPositive -> "Choose a duration longer than zero."
    is DurationValidation.TooShort ->
        "The shortest session is $minMinutes minute${if (minMinutes == 1) "" else "s"}."
    is DurationValidation.TooLong -> "The longest session is ${maxMinutes / 60} hours."
}
