package com.zenmode.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The manifest is part of the app's behaviour, and most of it cannot be checked
 * by reading Kotlin. These assertions catch the mistakes that would otherwise
 * only show up on a device: a missing permission, a service that is not
 * protected, an accessibility service any app could bind to.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestDeclarationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageName: String = context.packageName

    private fun declaredPermissions(): List<String> =
        context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()

    private fun service(className: String): ServiceInfo? =
        context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_SERVICES)
            .services
            ?.firstOrNull { it.name == className }

    private fun receiverNames(): List<String> =
        context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_RECEIVERS)
            .receivers
            ?.map { it.name }
            .orEmpty()

    @Test
    fun `only the permissions the app actually uses are requested`() {
        val permissions = declaredPermissions()

        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(permissions.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue(permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(permissions.contains("android.permission.POST_NOTIFICATIONS"))
    }

    @Test
    fun `no permission is requested that Zen Mode has no business holding`() {
        val permissions = declaredPermissions()

        // Calls are opened in the dialer, never placed by the app.
        assertFalse(permissions.contains("android.permission.CALL_PHONE"))
        assertFalse(permissions.contains("android.permission.READ_PHONE_STATE"))
        // There is no server, so there is nothing to send anywhere.
        assertFalse(permissions.contains("android.permission.INTERNET"))
        // The app list is read through a narrow <queries> element instead.
        assertFalse(permissions.contains("android.permission.QUERY_ALL_PACKAGES"))
        // Usage statistics, contacts, location and storage are never touched.
        assertFalse(permissions.contains("android.permission.PACKAGE_USAGE_STATS"))
        assertFalse(permissions.contains("android.permission.READ_CONTACTS"))
        assertFalse(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
    }

    @Test
    fun `the accessibility service can only be bound by the system`() {
        val declared = service("com.zenmode.app.service.ZenAccessibilityService")

        assertNotNull(declared)
        assertEquals(
            android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            declared!!.permission,
        )
        // Exported so the system can bind it; the permission is what keeps
        // everyone else out.
        assertTrue(declared.exported)
    }

    @Test
    fun `the foreground service is private and declares its type`() {
        val declared = service("com.zenmode.app.service.ZenForegroundService")

        assertNotNull(declared)
        assertFalse("No other app should be able to start it", declared!!.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            declared.foregroundServiceType,
        )
    }

    @Test
    fun `the boot and session-end receivers are registered`() {
        val receivers = receiverNames()

        assertTrue(receivers.contains("com.zenmode.app.service.BootReceiver"))
        assertTrue(receivers.contains("com.zenmode.app.service.SessionEndReceiver"))
    }

    @Test
    fun `the alarm receiver is not exposed to other apps`() {
        val receiver = context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_RECEIVERS)
            .receivers
            ?.first { it.name == "com.zenmode.app.service.SessionEndReceiver" }

        assertNotNull(receiver)
        assertFalse("Another app must not be able to end a session", receiver!!.exported)
    }
}
