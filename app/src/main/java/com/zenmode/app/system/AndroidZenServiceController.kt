package com.zenmode.app.system

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.zenmode.app.service.ZenForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts and stops [ZenForegroundService].
 *
 * Android refuses background foreground-service starts in a number of
 * situations, and refusing is its right. A refusal is reported back rather than
 * swallowed, so the caller can roll the session back instead of showing a Zen
 * screen with nothing behind it.
 */
@Singleton
class AndroidZenServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ZenServiceController {

    override fun startSessionService(): Boolean = try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ZenForegroundService::class.java).setAction(
                ZenForegroundService.ACTION_START,
            ),
        )
        true
    } catch (e: Exception) {
        val refused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            e is ForegroundServiceStartNotAllowedException
        Log.w(TAG, if (refused) "Android refused the foreground service start" else "Service start failed", e)
        false
    }

    override fun stopSessionService() {
        runCatching {
            context.startService(
                Intent(context, ZenForegroundService::class.java).setAction(
                    ZenForegroundService.ACTION_STOP,
                ),
            )
        }.onFailure {
            // The service is not running, which is what we wanted anyway.
            runCatching { context.stopService(Intent(context, ZenForegroundService::class.java)) }
        }
    }

    private companion object {
        const val TAG = "ZenService"
    }
}
