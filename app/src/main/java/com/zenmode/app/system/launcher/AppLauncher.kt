package com.zenmode.app.system.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Why an app could not be opened, in terms worth showing a person. */
sealed interface AppLaunchResult {
    data object Launched : AppLaunchResult

    /** The app is installed but has no way in — nothing to open. */
    data object NoLaunchableActivity : AppLaunchResult

    /** Gone, most likely uninstalled since the drawer was built. */
    data object NotInstalled : AppLaunchResult
}

/**
 * Opens an app the ordinary way.
 *
 * `getLaunchIntentForPackage` is Android's own answer to "how do I start this",
 * so the app opens exactly as it would from any other launcher. Nothing is
 * forced, no component is guessed, and a missing app is reported rather than
 * crashing the home screen.
 */
@Singleton
class AppLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun launch(packageName: String): AppLaunchResult {
        val intent = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull()
            ?: return AppLaunchResult.NoLaunchableActivity

        // A launcher starts apps in their own task, as a new task.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return try {
            context.startActivity(intent)
            AppLaunchResult.Launched
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Nothing to open for $packageName", e)
            AppLaunchResult.NotInstalled
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to open $packageName", e)
            AppLaunchResult.NoLaunchableActivity
        }
    }

    private companion object {
        const val TAG = "ZenAppLauncher"
    }
}
