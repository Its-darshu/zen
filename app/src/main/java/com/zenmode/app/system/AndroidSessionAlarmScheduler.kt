package com.zenmode.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.zenmode.app.service.SessionEndReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wakes the app when a session's time is up.
 *
 * An exact alarm is used when the user has granted one, because a focus session
 * ending five minutes late is a broken promise. When exact alarms are not
 * granted the app degrades to an inexact wake-up rather than nagging or
 * pretending: the session still ends, just possibly a little late if the app is
 * not running at the time.
 *
 * Only ever one alarm exists — the same request code replaces any previous one
 * — and it carries the session id it belongs to so a leftover alarm cannot end
 * a later session.
 */
@Singleton
class AndroidSessionAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SessionAlarmScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    override fun schedule(sessionId: Long, triggerAtMillis: Long): AlarmPrecision {
        val manager = alarmManager ?: return AlarmPrecision.UNAVAILABLE
        val pendingIntent = pendingIntent(sessionId, mutable = false) ?: return AlarmPrecision.UNAVAILABLE

        if (canScheduleExact()) {
            val scheduled = runCatching {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }.isSuccess
            if (scheduled) return AlarmPrecision.EXACT
            // Permission can be revoked between the check and the call.
            Log.w(TAG, "Exact alarm refused; falling back to an inexact wake-up")
        }

        return runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            AlarmPrecision.INEXACT
        }.getOrDefault(AlarmPrecision.UNAVAILABLE)
    }

    override fun cancel() {
        val manager = alarmManager ?: return
        // FLAG_NO_CREATE: if there is no alarm there is nothing to cancel.
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            // Must match the scheduled intent. PendingIntent matching compares
            // the action (and component), though not the extras — so the action
            // has to be set here too, or the alarm is never found and never
            // cancelled.
            alarmIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        existing?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun alarmIntent(sessionId: Long? = null): Intent =
        Intent(context, SessionEndReceiver::class.java).apply {
            action = SessionEndReceiver.ACTION_SESSION_END
            if (sessionId != null) putExtra(SessionEndReceiver.EXTRA_SESSION_ID, sessionId)
        }

    private fun pendingIntent(sessionId: Long, mutable: Boolean): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return runCatching {
            PendingIntent.getBroadcast(context, REQUEST_CODE, alarmIntent(sessionId), flags)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "ZenAlarm"

        /** One session at a time, so one request code: a new alarm replaces the old. */
        const val REQUEST_CODE = 1001
    }
}
