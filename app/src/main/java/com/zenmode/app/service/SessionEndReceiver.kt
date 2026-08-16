package com.zenmode.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zenmode.app.di.ApplicationScope
import com.zenmode.app.system.ZenModeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired by the alarm when a session's time is up.
 *
 * The receiver carries the id of the session it was scheduled for.
 * [ZenModeManager.completeIfDue] compares that against the session actually
 * running, so an alarm left over from a session the user already cancelled
 * cannot end the one running now — it simply finds a different id and does
 * nothing.
 */
@AndroidEntryPoint
class SessionEndReceiver : BroadcastReceiver() {

    @Inject lateinit var zenModeManager: ZenModeManager

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SESSION_END) return

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, NO_SESSION_ID)
        if (sessionId == NO_SESSION_ID) return

        // The work outlives onReceive, so hold the broadcast open for it.
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                zenModeManager.completeIfDue(expectedSessionId = sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not complete session $sessionId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SESSION_END = "com.zenmode.app.action.SESSION_END"
        const val EXTRA_SESSION_ID = "com.zenmode.app.extra.SESSION_ID"
        private const val NO_SESSION_ID = -1L
        private const val TAG = "SessionEndReceiver"
    }
}
