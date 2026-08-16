package com.zenmode.app.domain.model

/**
 * User settings (specification §16 and §22). Every value has a sensible default
 * so the app is fully usable before the user opens Settings once.
 */
data class ZenSettings(
    // Focus
    val defaultDurationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val confirmStart: Boolean = true,
    val completionNotification: Boolean = true,
    // Appearance
    val pureBlackZenScreen: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val use24HourClock: Boolean = true,
    // Behavior
    val showCallButton: Boolean = true,
    val confirmExit: Boolean = true,
    // Onboarding
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val DEFAULT_DURATION_MINUTES: Int = 25
    }
}
