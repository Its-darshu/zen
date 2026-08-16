package com.zenmode.app.testing

import com.zenmode.app.system.AlarmPrecision
import com.zenmode.app.system.SessionAlarmScheduler
import com.zenmode.app.system.ZenNotifier
import com.zenmode.app.system.ZenServiceController

/** Records what was scheduled, so tests can assert on alarm handling. */
class FakeSessionAlarmScheduler(
    private var exactAllowed: Boolean = true,
) : SessionAlarmScheduler {

    var scheduledSessionId: Long? = null
        private set
    var scheduledAt: Long? = null
        private set
    var scheduleCount = 0
        private set
    var cancelCount = 0
        private set

    val hasPendingAlarm: Boolean get() = scheduledSessionId != null

    override fun schedule(sessionId: Long, triggerAtMillis: Long): AlarmPrecision {
        scheduledSessionId = sessionId
        scheduledAt = triggerAtMillis
        scheduleCount++
        return if (exactAllowed) AlarmPrecision.EXACT else AlarmPrecision.INEXACT
    }

    override fun cancel() {
        scheduledSessionId = null
        scheduledAt = null
        cancelCount++
    }

    override fun canScheduleExact(): Boolean = exactAllowed

    fun setExactAllowed(allowed: Boolean) {
        exactAllowed = allowed
    }
}

class FakeZenServiceController(
    /** Set false to simulate Android refusing a background service start. */
    var startSucceeds: Boolean = true,
) : ZenServiceController {

    var running = false
        private set
    var startAttempts = 0
        private set
    var stopCount = 0
        private set

    override fun startSessionService(): Boolean {
        startAttempts++
        if (!startSucceeds) return false
        running = true
        return true
    }

    override fun stopSessionService() {
        stopCount++
        running = false
    }
}

class FakeZenNotifier(
    private var notificationsAllowed: Boolean = true,
) : ZenNotifier {

    var completionsPosted = 0
        private set
    var lastFocusedSeconds: Long? = null
        private set
    var cancelCount = 0
        private set

    override fun notifySessionCompleted(focusedSeconds: Long) {
        completionsPosted++
        lastFocusedSeconds = focusedSeconds
    }

    override fun cancelCompletionNotification() {
        cancelCount++
    }

    override fun canPostNotifications(): Boolean = notificationsAllowed

    fun setNotificationsAllowed(allowed: Boolean) {
        notificationsAllowed = allowed
    }
}
