package com.zenmode.app.system

import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.usecase.CheckAccessibilityPermissionUseCase
import com.zenmode.app.domain.usecase.GetBlockedAppsUseCase
import com.zenmode.app.testing.FakeAccessibilityPermissionMonitor
import com.zenmode.app.testing.FakeBlockedAppRepository
import com.zenmode.app.testing.FakeSessionAlarmScheduler
import com.zenmode.app.testing.FakeZenNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app reports about the platform. Every value has to reflect the real
 * state, because the UI turns these into promises about what a session will do.
 */
class ZenSetupStatusProviderTest {

    private val monitor = FakeAccessibilityPermissionMonitor(enabled = false)
    private val blockedApps = FakeBlockedAppRepository()
    private val alarms = FakeSessionAlarmScheduler()
    private val notifier = FakeZenNotifier()

    private val provider = ZenSetupStatusProvider(
        checkAccessibilityPermission = CheckAccessibilityPermissionUseCase(monitor),
        getBlockedApps = GetBlockedAppsUseCase(blockedApps),
        alarmScheduler = alarms,
        notifier = notifier,
    )

    @Test
    fun `a fresh install reports blocking as not ready`() = runTest {
        val status = provider.observe().first()

        assertFalse(status.accessibilityEnabled)
        assertEquals(0, status.blockedAppCount)
        assertFalse(status.blockingReady)
    }

    @Test
    fun `blocking is only ready with both the permission and some apps`() = runTest {
        monitor.setEnabled(true)
        assertFalse(provider.observe().first().blockingReady)

        blockedApps.setBlocked("com.example.social", "Social", enabled = true)
        assertTrue(provider.observe().first().blockingReady)

        monitor.setEnabled(false)
        assertFalse(provider.observe().first().blockingReady)
    }

    @Test
    fun `granting accessibility is observed without a refresh`() = runTest {
        assertFalse(provider.observe().first().accessibilityEnabled)

        monitor.setEnabled(true)

        assertTrue(provider.observe().first().accessibilityEnabled)
    }

    @Test
    fun `denied exact alarms are reported as a possible delay`() = runTest {
        alarms.setExactAllowed(false)

        val status = provider.observe().first()

        assertFalse(status.exactAlarmsAvailable)
        assertTrue(status.sessionEndMayBeDelayed)
    }

    @Test
    fun `granted exact alarms report no delay`() = runTest {
        alarms.setExactAllowed(true)

        val status = provider.observe().first()

        assertTrue(status.exactAlarmsAvailable)
        assertFalse(status.sessionEndMayBeDelayed)
    }

    @Test
    fun `notification access is reported as the platform gives it`() = runTest {
        assertTrue(provider.observe().first().notificationsEnabled)

        notifier.setNotificationsAllowed(false)

        // Android does not broadcast this one, so it is re-read on refresh —
        // which the screens do when they come back from Android's settings.
        provider.refresh()
        assertFalse(provider.observe().first().notificationsEnabled)
    }

    @Test
    fun `refreshing picks up an exact-alarm grant made in Android settings`() = runTest {
        alarms.setExactAllowed(false)
        assertFalse(provider.observe().first().exactAlarmsAvailable)

        alarms.setExactAllowed(true)
        provider.refresh()

        assertTrue(provider.observe().first().exactAlarmsAvailable)
    }

    @Test
    fun `the blocked app count follows the user's selection`() = runTest {
        blockedApps.setBlockedApps(
            listOf(
                BlockedApp("com.a", "A", enabled = true),
                BlockedApp("com.b", "B", enabled = true),
            ),
        )

        assertEquals(2, provider.observe().first().blockedAppCount)
    }

    @Test
    fun `every capability is independent of the others`() = runTest {
        monitor.setEnabled(true)
        alarms.setExactAllowed(false)
        notifier.setNotificationsAllowed(false)
        provider.refresh()

        val status = provider.observe().first()

        assertTrue(status.accessibilityEnabled)
        assertFalse(status.exactAlarmsAvailable)
        assertFalse(status.notificationsEnabled)
    }
}
