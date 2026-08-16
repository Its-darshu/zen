package com.zenmode.app.system

/**
 * The Android capabilities [ZenModeManager] coordinates, behind interfaces.
 *
 * The manager decides *what* should happen to system state; these decide *how*
 * on this platform. Keeping them as interfaces means the manager's rules —
 * including the stale-alarm and rollback rules — are testable without a device.
 */

/** How precisely the platform agreed to wake us at the end of a session. */
enum class AlarmPrecision {
    /** Exact: the session ends on time even in Doze. */
    EXACT,

    /**
     * Inexact: the user has not granted exact-alarm access, so Android may
     * batch the wake-up. Completion can be a few minutes late when the app is
     * not running. Never reported to the user as exact.
     */
    INEXACT,

    /** The alarm could not be scheduled at all. */
    UNAVAILABLE,
}

interface SessionAlarmScheduler {

    /**
     * Wakes the app at [triggerAtMillis] to finish [sessionId].
     *
     * Replaces any alarm already scheduled: only one session runs at a time.
     */
    fun schedule(sessionId: Long, triggerAtMillis: Long): AlarmPrecision

    /** Drops the pending alarm, if any. */
    fun cancel()

    /** Whether the platform would currently grant an exact alarm. */
    fun canScheduleExact(): Boolean
}

/** Starts and stops the ongoing-session foreground service. */
interface ZenServiceController {

    /**
     * @return false when Android refused to start the service — the caller must
     *   then roll back rather than pretend a session is running.
     */
    fun startSessionService(): Boolean

    fun stopSessionService()
}

/** Posts the completion notification. */
interface ZenNotifier {

    fun notifySessionCompleted(focusedSeconds: Long)

    fun cancelCompletionNotification()

    /**
     * Whether Android currently lets the app post notifications. False is a
     * normal state, not an error: sessions run either way.
     */
    fun canPostNotifications(): Boolean
}
