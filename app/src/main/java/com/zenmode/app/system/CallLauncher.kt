package com.zenmode.app.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The CALL action on the Zen screen (specification §12).
 *
 * Opens the user's own dialer with `ACTION_DIAL` and stops there. Zen Mode
 * does not hold `CALL_PHONE`, never places a call itself, never dials a number
 * on the user's behalf, and never touches calls in progress — incoming,
 * outgoing or emergency.
 *
 * The dialer is also on the never-blocked list, so a session cannot get between
 * the user and a phone call.
 */
@Singleton
class CallLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * The dialer intent: no number, no `CALL_PHONE`, nothing dialled
     * automatically. The user types and confirms in their own dialer.
     */
    fun dialerIntent(): Intent = Intent(Intent.ACTION_DIAL)

    /**
     * @return false when the device has no dialer to open — a tablet without
     *   telephony, for instance — so the caller can say so plainly instead of
     *   the button appearing broken.
     */
    fun openDialer(): Boolean {
        val intent = dialerIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No dialer available on this device", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not open the dialer", e)
            false
        }
    }

    private companion object {
        const val TAG = "ZenCall"
    }
}
