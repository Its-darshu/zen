package com.zenmode.app.domain.model

/**
 * The state of the countdown at one instant.
 *
 * A snapshot is a *value derived from timestamps*, never a counter that is kept
 * ticking in memory (specification §8). Anything that needs a live countdown
 * re-derives a snapshot; losing one costs nothing.
 */
data class TimerSnapshot(
    val isRunning: Boolean,
    val remainingSeconds: Long,
    val elapsedSeconds: Long,
    val plannedDurationSeconds: Long,
    val progress: Float,
    val isExpired: Boolean,
) {
    companion object {
        /** No session running. */
        val Idle = TimerSnapshot(
            isRunning = false,
            remainingSeconds = 0L,
            elapsedSeconds = 0L,
            plannedDurationSeconds = 0L,
            progress = 0f,
            isExpired = false,
        )
    }
}
