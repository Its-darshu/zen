package com.zenmode.app.system

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the device in the Zen session using Android's own lock task mode.
 *
 * This is the *only* place that knows how strongly a session can be enforced.
 * The domain layer knows nothing about it: it only knows a session is active.
 *
 * Two very different things are possible here, and the difference matters:
 *
 * - **Screen pinning** — available on any device. `startLockTask()` from an app
 *   that no device owner has allow-listed pins the screen: Home and Recents
 *   stop leaving the app, but Android keeps its own escape (hold Back and
 *   Overview) and shows the user how to use it. That escape cannot be removed,
 *   and this app does not try.
 *
 * - **Lock task proper** — only when a device owner has allow-listed this app.
 *   Home and Recents are blocked, and the keyguard is disabled while the task
 *   is locked, so the screen going off and on returns straight to the session.
 *
 * Nothing here disables Android security, hides system controls, or blocks
 * uninstall. The power menu stays reachable in both modes, and every route out
 * documented in `docs/STRICT_MODE_SETUP.md` keeps working.
 */
@Singleton
class ZenLockdownController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val devicePolicyManager: DevicePolicyManager?
        get() = context.getSystemService(DevicePolicyManager::class.java)

    private val activityManager: ActivityManager?
        get() = context.getSystemService(ActivityManager::class.java)

    /** True when this app has been provisioned as the device owner. */
    fun isDeviceOwner(): Boolean = runCatching {
        devicePolicyManager?.isDeviceOwnerApp(context.packageName) == true
    }.getOrDefault(false)

    /**
     * What this device can actually offer today.
     *
     * `isLockTaskPermitted` is the honest question: it is true only when a
     * device owner has allow-listed us, which is exactly when lock task behaves
     * as a kiosk rather than as screen pinning.
     */
    fun capability(): LockdownCapability {
        val allowListed = runCatching {
            devicePolicyManager?.isLockTaskPermitted(context.packageName) == true
        }.getOrDefault(false)

        return when {
            allowListed -> LockdownCapability.KIOSK
            // Every supported version can pin the screen.
            else -> LockdownCapability.SCREEN_PINNING
        }
    }

    /**
     * Allow-lists this app for lock task, if — and only if — the user has
     * already made it the device owner.
     *
     * Safe to call on every launch: it does nothing at all on an ordinary
     * device, because a non-owner cannot set these policies.
     */
    fun applyDeviceOwnerPoliciesIfPossible() {
        if (!isDeviceOwner()) return
        val dpm = devicePolicyManager ?: return
        val admin = ZenDeviceAdminReceiver.componentName(context)

        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // The power menu stays available on purpose: it is the user's
                // way to reboot out of a session, and removing it would make
                // the device genuinely inescapable.
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                )
            }
        }.onFailure { Log.w(TAG, "Could not apply device owner policies", it) }
    }

    /** True while Android currently has a task locked or pinned. */
    fun isLockTaskActive(): Boolean = runCatching {
        activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }.getOrDefault(false)

    /**
     * Asks Android to hold this activity.
     *
     * @return false when the platform refused — the session carries on either
     *   way, just without the device being held.
     */
    fun enter(activity: Activity): Boolean = runCatching {
        activity.startLockTask()
        true
    }.getOrElse {
        Log.w(TAG, "Android refused to start lock task", it)
        false
    }

    /** Releases the device. Always safe to call, even when nothing is locked. */
    fun exit(activity: Activity) {
        runCatching { activity.stopLockTask() }
            .onFailure { Log.w(TAG, "Could not stop lock task", it) }
    }

    private companion object {
        const val TAG = "ZenLockdown"
    }
}
