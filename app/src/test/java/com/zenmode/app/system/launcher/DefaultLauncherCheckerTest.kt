package com.zenmode.app.system.launcher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Reading the home-app state honestly. The app can never *set* this, so the
 * only thing worth testing is that it reports what Android actually says.
 *
 * Home resolution is stubbed explicitly rather than left to the test manifest —
 * this app declares a HOME activity of its own, so without stubbing every case
 * would resolve to it.
 */
// setResolveInfosForIntent is deprecated in the current Robolectric with no
// stable replacement for overriding manifest-declared intent resolution.
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class DefaultLauncherCheckerTest {

    private val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val checker = DefaultLauncherChecker(application)

    private val homeIntent: Intent
        get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /**
     * Once this app is installed Home always resolves to *something* — this
     * app, another launcher, or Android's chooser — so there is no "resolves to
     * nothing" case to test on a real device.
     */
    private fun resolveHomeTo(packageName: String, className: String = "HomeActivity") {
        val shadow = shadowOf(application.packageManager)
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = className
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        }
        shadow.setResolveInfosForIntent(homeIntent, listOf(resolveInfo))
    }

    @Test
    fun `another launcher being the home app is reported as such`() {
        resolveHomeTo("com.example.otherlauncher")

        assertEquals(DefaultLauncherState.OTHER_LAUNCHER, checker.state())
        assertEquals("com.example.otherlauncher", checker.currentDefaultPackage())
        assertFalse(checker.isZenLauncherDefault())
    }

    @Test
    fun `this app being the home app is reported as such`() {
        resolveHomeTo(application.packageName)

        assertEquals(DefaultLauncherState.ZEN_LAUNCHER, checker.state())
        assertTrue(checker.isZenLauncherDefault())
    }

    @Test
    fun `Android's chooser is not mistaken for a launcher`() {
        // With several launchers and no default, Home resolves to the system
        // resolver, which must read as "not chosen" rather than as a launcher.
        resolveHomeTo("android", "com.android.internal.app.ResolverActivity")

        assertEquals(DefaultLauncherState.NOT_CHOSEN, checker.state())
        assertNull(checker.currentDefaultPackage())
    }

    @Test
    fun `the home settings intent targets Android's own home-app screen`() {
        val intent = checker.homeSettingsIntent()

        assertEquals(android.provider.Settings.ACTION_HOME_SETTINGS, intent.action)
    }
}
