package com.zenmode.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zenmode.app.system.ZenNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REQUIRES A PHYSICAL DEVICE OR EMULATOR.
 *
 * Run with `./gradlew :app:connectedDebugAndroidTest`. These have **not** been
 * executed — no device was attached during development — so they are written to
 * be run, not reported as passing.
 *
 * They cover the parts of the integration that only a real Android runtime can
 * answer: whether the system accepts the service declarations, whether the
 * notification channels are created as configured, and whether the accessibility
 * service is registered with the platform.
 *
 * The behaviours that need a *user action* — granting accessibility access,
 * opening a blocked app, rebooting mid-session — are in the manual checklist in
 * the phase report instead, because no automated test can grant an
 * accessibility service on the user's behalf.
 */
@RunWith(AndroidJUnit4::class)
class ZenSystemIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun notificationChannelsAreCreatedWithTheIntendedImportance() {
        ZenNotifications(context).ensureChannels()

        val manager = context.getSystemService(NotificationManager::class.java)
        val active = manager.getNotificationChannel(ZenNotifications.CHANNEL_ACTIVE)
        val completion = manager.getNotificationChannel(ZenNotifications.CHANNEL_COMPLETION)

        assertNotNull(active)
        assertNotNull(completion)
        // The ongoing notification must never buzz during a focus session.
        assertEquals(NotificationManager.IMPORTANCE_LOW, active.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, completion.importance)
    }

    @Test
    fun theActiveNotificationCountsDownFromTheSessionEndTimestamp() {
        val notifications = ZenNotifications(context)
        notifications.ensureChannels()
        val endsAt = System.currentTimeMillis() + 1_500_000L

        val notification = notifications.buildActiveNotification(
            scheduledEndAt = endsAt,
            remainingSeconds = 1_500L,
        )

        // `when` carries the end time; the system draws the countdown from it,
        // so the notification cannot drift away from the stored session.
        assertEquals(endsAt, notification.`when`)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun theAccessibilityServiceIsRegisteredWithThePlatform() {
        val manager = context.getSystemService(AccessibilityManager::class.java)

        val installed = manager.installedAccessibilityServiceList
            .firstOrNull { it.resolveInfo.serviceInfo.packageName == context.packageName }

        assertNotNull("Zen Mode's accessibility service should be installed", installed)
        // The service must not be able to read screen content: it only needs the
        // foreground package name.
        assertEquals(0, installed!!.capabilities and CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT)
    }

    @Test
    fun theAccessibilityServiceIsOffUntilTheUserEnablesIt() {
        val manager = context.getSystemService(AccessibilityManager::class.java)

        val enabled = manager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }

        // Nothing the app does can grant this; a fresh install must have it off.
        assertFalse("The app must never enable its own accessibility service", enabled)
    }

    @Test
    fun theForegroundServiceIsDeclaredAndPrivate() {
        val service = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services
            ?.firstOrNull { it.name == ZenForegroundService::class.java.name }

        assertNotNull(service)
        assertFalse(service!!.exported)
    }

    @Test
    fun theSessionEndAlarmResolvesToTheReceiver() {
        val intent = Intent(context, SessionEndReceiver::class.java)
            .setAction(SessionEndReceiver.ACTION_SESSION_END)

        val resolved = context.packageManager.queryBroadcastReceivers(intent, 0)

        assertTrue("The alarm must have a receiver to deliver to", resolved.isNotEmpty())
    }

    @Test
    fun theBootReceiverIsRegisteredForRestartRecovery() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName)

        val resolved = context.packageManager.queryBroadcastReceivers(intent, 0)

        assertTrue(resolved.any { it.activityInfo.name == BootReceiver::class.java.name })
    }

    private companion object {
        const val CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT = 0x00000001
        const val FEEDBACK_ALL_MASK = -1
    }
}
