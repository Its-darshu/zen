package com.zenmode.app.system

import com.zenmode.app.domain.usecase.CheckAccessibilityPermissionUseCase
import com.zenmode.app.domain.usecase.GetBlockedAppsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the platform will and will not currently do for a Zen session.
 *
 * Every field is read from Android, never assumed. The point is that the UI can
 * describe the *actual* state of things — including the parts Android has
 * denied — instead of promising behaviour the app cannot deliver.
 */
data class ZenSetupStatus(
    val accessibilityEnabled: Boolean = false,
    val blockedAppCount: Int = 0,
    val exactAlarmsAvailable: Boolean = true,
    val notificationsEnabled: Boolean = true,
) {
    /** True only when a session would actually block anything. */
    val blockingReady: Boolean get() = accessibilityEnabled && blockedAppCount > 0

    /**
     * True when Android may run the end-of-session alarm late. Sessions still
     * end; they may just end a few minutes after the countdown reaches zero if
     * the app is not running at the time.
     */
    val sessionEndMayBeDelayed: Boolean get() = !exactAlarmsAvailable
}

/**
 * Observes [ZenSetupStatus].
 *
 * Accessibility and the blocklist push changes; exact-alarm and notification
 * access do not, so they are re-read whenever [refresh] is called — which the
 * screens do when they resume, i.e. when the user comes back from Android's
 * settings.
 */
@Singleton
class ZenSetupStatusProvider @Inject constructor(
    private val checkAccessibilityPermission: CheckAccessibilityPermissionUseCase,
    private val getBlockedApps: GetBlockedAppsUseCase,
    private val alarmScheduler: SessionAlarmScheduler,
    private val notifier: ZenNotifier,
) {

    private val refreshTrigger = MutableStateFlow(0)

    fun observe(): Flow<ZenSetupStatus> = combine(
        checkAccessibilityPermission(),
        getBlockedApps.enabledPackages(),
        refreshTrigger,
    ) { accessibilityEnabled, blockedPackages, _ ->
        ZenSetupStatus(
            accessibilityEnabled = accessibilityEnabled,
            blockedAppCount = blockedPackages.size,
            exactAlarmsAvailable = alarmScheduler.canScheduleExact(),
            notificationsEnabled = notifier.canPostNotifications(),
        )
    }

    /** Re-reads the states Android does not broadcast. */
    fun refresh() {
        refreshTrigger.value = refreshTrigger.value + 1
    }
}
