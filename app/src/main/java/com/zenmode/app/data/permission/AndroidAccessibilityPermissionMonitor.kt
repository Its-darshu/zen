package com.zenmode.app.data.permission

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.zenmode.app.domain.permission.AccessibilityPermissionMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether the user has switched Zen Mode's accessibility service on.
 *
 * This only *reads* the grant state — it never asks for it and cannot grant it.
 * Turning the service on is something only the user can do, in Android's own
 * settings, and they can turn it off again there at any time.
 *
 * The state is watched two ways because neither alone is sufficient: the
 * accessibility-state listener misses changes while the app is backgrounded on
 * some versions, and the settings observer catches the underlying secure
 * setting being edited directly.
 */
@Singleton
class AndroidAccessibilityPermissionMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AccessibilityPermissionMonitor {

    private val accessibilityManager: AccessibilityManager?
        get() = context.getSystemService(AccessibilityManager::class.java)

    override val isEnabled: Flow<Boolean> = callbackFlow {
        trySend(readIsEnabled())

        val manager = accessibilityManager
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener {
            trySend(readIsEnabled())
        }
        manager?.addAccessibilityStateChangeListener(stateListener)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(readIsEnabled())
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                observer,
            )
        }

        awaitClose {
            manager?.removeAccessibilityStateChangeListener(stateListener)
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }.distinctUntilChanged()

    override suspend fun isEnabledNow(): Boolean = readIsEnabled()

    /**
     * True when one of this app's own services appears in the enabled list.
     *
     * Matching on the package rather than a class name keeps this honest if the
     * service class is ever renamed, and never reports another app's service as
     * ours.
     */
    private fun readIsEnabled(): Boolean {
        val manager = accessibilityManager ?: return false
        val runningServices = runCatching {
            manager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)
        }.getOrNull().orEmpty()

        if (runningServices.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }) {
            return true
        }

        // Fallback for the window between the user granting access and the
        // manager reflecting it.
        val enabledSetting = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()

        return enabledSetting
            .split(SERVICE_SEPARATOR)
            .any { it.substringBefore('/') == context.packageName }
    }

    private companion object {
        const val FEEDBACK_ALL_MASK = -1
        const val SERVICE_SEPARATOR = ':'
    }
}
