package com.zenmode.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the Android settings pages where the user grants — or revokes — the
 * access Zen Mode can use.
 *
 * The app can only take the user to these screens. Every decision is made in
 * Android's own UI, by the user, and can be undone there at any time.
 */
@Singleton
class SystemSettingsLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Where the user turns app blocking on or off. */
    fun openAccessibilitySettings(): Boolean = openFirstAvailable(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    /**
     * Where the user allows exact alarms, so sessions end on time (Android 12+).
     *
     * On older versions exact alarms need no permission, so there is nothing to
     * open and this reports false rather than opening something irrelevant.
     */
    fun openExactAlarmSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return openFirstAvailable(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts(PACKAGE_SCHEME, context.packageName, null),
            ),
            appDetailsIntent(),
        )
    }

    /** Where the user allows or blocks Zen Mode's notifications. */
    fun openNotificationSettings(): Boolean = openFirstAvailable(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetailsIntent(),
    )

    private fun appDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts(PACKAGE_SCHEME, context.packageName, null),
    )

    private fun openFirstAvailable(vararg intents: Intent): Boolean {
        intents.forEach { intent ->
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return true
        }
        return false
    }

    private companion object {
        const val PACKAGE_SCHEME = "package"
    }
}
