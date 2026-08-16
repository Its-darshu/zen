package com.zenmode.app.system

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zenmode.app.service.SessionEndReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The real scheduler against Robolectric's AlarmManager: what is scheduled, when
 * it fires, and that cancelling actually removes it.
 */
// ShadowAlarmManager's accessors are deprecated in the current Robolectric
// without a stable replacement for reading a scheduled alarm's trigger time.
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class AndroidSessionAlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AndroidSessionAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = AndroidSessionAlarmScheduler(context)
    }

    @Test
    fun `scheduling sets one alarm at the session's end time`() {
        val endsAt = 1_700_000_000_000L

        val precision = scheduler.schedule(sessionId = 7L, triggerAtMillis = endsAt)

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(scheduled)
        assertEquals(endsAt, scheduled!!.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.type)
        assertTrue(precision == AlarmPrecision.EXACT || precision == AlarmPrecision.INEXACT)
    }

    @Test
    fun `the alarm carries the session it belongs to`() {
        scheduler.schedule(sessionId = 42L, triggerAtMillis = 1_700_000_000_000L)

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm!!
        val intent = shadowOf(scheduled.operation).savedIntent

        assertEquals(SessionEndReceiver.ACTION_SESSION_END, intent.action)
        assertEquals(42L, intent.getLongExtra(SessionEndReceiver.EXTRA_SESSION_ID, -1L))
    }

    @Test
    fun `rescheduling replaces the previous alarm rather than adding one`() {
        scheduler.schedule(sessionId = 1L, triggerAtMillis = 1_000L)
        scheduler.schedule(sessionId = 2L, triggerAtMillis = 2_000L)

        val alarms = shadowOf(alarmManager).scheduledAlarms

        assertEquals(1, alarms.size)
        assertEquals(2_000L, alarms.single().triggerAtTime)
    }

    @Test
    fun `cancelling removes the pending alarm`() {
        scheduler.schedule(sessionId = 1L, triggerAtMillis = 1_000L)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun `cancelling when nothing is scheduled is harmless`() {
        scheduler.cancel()
        scheduler.cancel()

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun `exact scheduling is only claimed when the platform allows it`() {
        // Robolectric grants exact alarms by default; the point is that the
        // scheduler asks rather than assuming.
        val precision = scheduler.schedule(sessionId = 1L, triggerAtMillis = 1_000L)

        val expected = if (scheduler.canScheduleExact()) {
            AlarmPrecision.EXACT
        } else {
            AlarmPrecision.INEXACT
        }
        assertEquals(expected, precision)
    }
}
