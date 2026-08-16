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
 * Puts a session back together after a reboot (specification §27).
 *
 * The database survived the restart; the alarm and the service did not.
 * [ZenModeManager.recover] reads the session back and either completes it —
 * if it expired while the device was off — or reschedules the alarm and starts
 * the service again.
 *
 * Recent Android versions may refuse to start a foreground service from a boot
 * broadcast. That refusal is accepted rather than worked around: the session
 * stays in the database, the alarm still ends it on time, and the service comes
 * back the next time the app is opened. Nothing here reports a restoration that
 * did not happen.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var zenModeManager: ZenModeManager

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                zenModeManager.recover()
            } catch (e: Exception) {
                Log.w(TAG, "Could not recover the active session after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ZenBootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Sent instead of BOOT_COMPLETED on devices with direct boot, and
            // after the app is updated.
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
