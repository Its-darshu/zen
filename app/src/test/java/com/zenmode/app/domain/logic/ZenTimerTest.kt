package com.zenmode.app.domain.logic

import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.TimerSnapshot
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.zenSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenTimerTest {

    private val clock = FakeZenClock(now = 1_000_000L)
    private val timer = ZenTimer(clock)

    private val session = zenSession(
        startedAt = 1_000_000L,
        plannedDurationSeconds = 1_500L,
        status = SessionStatus.ACTIVE,
    )

    @Test
    fun `no session reads as idle`() {
        assertEquals(TimerSnapshot.Idle, timer.snapshot(null))
        assertFalse(timer.snapshot(null).isRunning)
    }

    @Test
    fun `a snapshot describes the session at the current clock`() {
        clock.advanceSeconds(500)

        val snapshot = timer.snapshot(session)

        assertTrue(snapshot.isRunning)
        assertEquals(1_000L, snapshot.remainingSeconds)
        assertEquals(500L, snapshot.elapsedSeconds)
        assertEquals(1_500L, snapshot.plannedDurationSeconds)
        assertEquals(1f / 3f, snapshot.progress, 0.0001f)
        assertFalse(snapshot.isExpired)
    }

    @Test
    fun `snapshots follow the clock without any state of their own`() {
        assertEquals(1_500L, timer.snapshot(session).remainingSeconds)

        clock.advanceSeconds(1_499)
        assertEquals(1L, timer.snapshot(session).remainingSeconds)

        clock.advanceSeconds(1)
        assertEquals(0L, timer.snapshot(session).remainingSeconds)
        assertTrue(timer.snapshot(session).isExpired)
    }

    @Test
    fun `a session is expired exactly at its end instant`() {
        clock.now = session.scheduledEndAt - 1L
        assertFalse(timer.isExpired(session))

        clock.now = session.scheduledEndAt
        assertTrue(timer.isExpired(session))
    }

    @Test
    fun `the recorded duration is what actually elapsed when stopped early`() {
        assertEquals(600L, timer.actualDurationSeconds(session, session.startedAt + 600_000L))
    }

    @Test
    fun `the recorded duration is capped when completion is processed late`() {
        // The device slept through the end of the session and the alarm ran an
        // hour late; the user still focused for the 25 minutes they asked for.
        val oneHourLate = session.scheduledEndAt + 3_600_000L

        assertEquals(1_500L, timer.actualDurationSeconds(session, oneHourLate))
    }

    @Test
    fun `a backwards clock cannot produce a negative recorded duration`() {
        assertEquals(0L, timer.actualDurationSeconds(session, session.startedAt - 5_000L))
    }

    @Test
    fun `snapshotAt ignores the clock so callers can ask about any instant`() {
        val snapshot = timer.snapshotAt(session, session.startedAt + 750_000L)

        assertEquals(750L, snapshot.remainingSeconds)
        assertEquals(750L, snapshot.elapsedSeconds)
    }
}
