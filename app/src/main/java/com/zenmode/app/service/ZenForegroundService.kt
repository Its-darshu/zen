package com.zenmode.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.system.ZenModeManager
import com.zenmode.app.system.ZenNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a running Zen session alive and visible (specification §26).
 *
 * The service holds no timer of its own. It reads the active session from the
 * database and re-derives the remaining time from `scheduledEndAt - now` every
 * time it wakes, so the countdown is identical to the one on screen and
 * survives the service being restarted.
 *
 * It wakes on minute boundaries rather than every second — the notification's
 * countdown is drawn by the system chronometer — and once more exactly when the
 * session is due to end. That keeps it cheap while still ending sessions on
 * time whenever the process is alive.
 */
@AndroidEntryPoint
class ZenForegroundService : Service() {

    @Inject lateinit var zenModeManager: ZenModeManager

    @Inject lateinit var notifications: ZenNotifications

    @Inject lateinit var getActiveSession: GetActiveSessionUseCase

    @Inject lateinit var clock: ZenClock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        // Must go into the foreground promptly, before the session has been read
        // back, so this first notification carries no countdown yet.
        if (!startForegroundSafely(session = null)) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            observeSession()
        }
        // Re-created after being killed: onStartCommand runs again with a null
        // intent and the session is read from the database as usual.
        return START_STICKY
    }

    private fun observeSession() {
        serviceScope.launch {
            getActiveSession().collectLatest { session ->
                if (session == null) {
                    stopSelfSafely()
                    return@collectLatest
                }
                trackUntilEnd(session)
            }
        }
    }

    /**
     * Keeps the notification current until the session's end time, then hands
     * completion to the manager — which is the only thing allowed to decide a
     * session is over.
     */
    private suspend fun trackUntilEnd(session: ZenSession) {
        while (serviceScope.isActive) {
            val now = clock.nowMillis()

            if (session.isExpiredAt(now)) {
                zenModeManager.completeIfDue(session.id)
                return
            }

            startForegroundSafely(session)

            val untilEnd = session.scheduledEndAt - now
            val untilNextMinute = MILLIS_PER_MINUTE - (now % MILLIS_PER_MINUTE)
            delay(minOf(untilEnd, untilNextMinute).coerceAtLeast(MIN_DELAY_MILLIS))
        }
    }

    private fun startForegroundSafely(session: ZenSession?): Boolean {
        val remainingSeconds = session?.remainingSecondsAt(clock.nowMillis()) ?: 0L
        val notification = notifications.buildActiveNotification(
            scheduledEndAt = session?.scheduledEndAt ?: clock.nowMillis(),
            remainingSeconds = remainingSeconds,
        )

        return try {
            ServiceCompat.startForeground(
                this,
                ZenNotifications.ID_ACTIVE,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
            true
        } catch (e: Exception) {
            // Includes ForegroundServiceStartNotAllowedException on newer
            // versions. The session itself is unaffected: it lives in the
            // database and the alarm still ends it.
            Log.w(TAG, "Could not run in the foreground", e)
            false
        }
    }

    private fun stopSelfSafely() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        started = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.zenmode.app.action.START_SESSION_SERVICE"
        const val ACTION_STOP = "com.zenmode.app.action.STOP_SESSION_SERVICE"
        private const val TAG = "ZenForegroundService"
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MIN_DELAY_MILLIS = 250L
    }
}
