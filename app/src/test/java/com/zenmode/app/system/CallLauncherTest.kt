package com.zenmode.app.system

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The call action (specification §12). */
@RunWith(RobolectricTestRunner::class)
class CallLauncherTest {

    private val launcher = CallLauncher(ApplicationProvider.getApplicationContext())

    @Test
    fun `the call action opens the dialer rather than placing a call`() {
        val intent = launcher.dialerIntent()

        assertEquals(Intent.ACTION_DIAL, intent.action)
        // ACTION_CALL would place the call directly and needs CALL_PHONE; this
        // app has neither.
        assertFalse(intent.action == Intent.ACTION_CALL)
    }

    @Test
    fun `no number is dialled on the user's behalf`() {
        val intent = launcher.dialerIntent()

        assertNull(intent.data)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
    }

    @Test
    fun `the intent carries no expectation of a result or a target app`() {
        val intent = launcher.dialerIntent()

        assertNull(intent.component)
        assertNull(intent.`package`)
    }

    @Test
    fun `a device with no dialer reports failure instead of crashing`() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Makes Robolectric behave like a device with nothing to handle the
        // intent — a tablet without telephony, for instance.
        shadowOf(application).checkActivities(true)

        assertFalse(launcher.openDialer())
    }

    @Test
    fun `opening the dialer launches the dial intent and nothing else`() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(application.packageManager).addActivityIfNotPresent(
            android.content.ComponentName("com.example.dialer", "DialerActivity"),
        )
        shadowOf(application.packageManager).addIntentFilterForActivity(
            android.content.ComponentName("com.example.dialer", "DialerActivity"),
            android.content.IntentFilter(Intent.ACTION_DIAL),
        )

        assertTrue(launcher.openDialer())

        val launched = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, launched.action)
        assertNull(launched.data)
    }
}
