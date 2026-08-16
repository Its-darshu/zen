package com.zenmode.app.system

import com.zenmode.app.domain.logic.ZenSessionStateMachine
import com.zenmode.app.domain.logic.ZenTimer
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.usecase.CompleteZenSessionUseCase
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.domain.usecase.StartZenSessionUseCase
import com.zenmode.app.domain.usecase.StopZenSessionUseCase
import com.zenmode.app.testing.FakeBlockedAppRepository
import com.zenmode.app.testing.FakeSessionAlarmScheduler
import com.zenmode.app.testing.FakeSessionRepository
import com.zenmode.app.testing.FakeSettingsRepository
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.FakeZenModeRepository
import com.zenmode.app.testing.FakeZenNotifier
import com.zenmode.app.testing.FakeZenServiceController
import com.zenmode.app.testing.SessionStore
import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.ZenSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The coordinator's rules: what happens to the alarm, the service and the
 * notification as a session starts, stops, completes and recovers.
 */
class ZenModeManagerTest {

    private val clock = FakeZenClock(now = 1_000_000L)
    private val store = SessionStore()
    private val zenModeRepository = FakeZenModeRepository(store)
    private val sessionRepository = FakeSessionRepository(store)
    private val blockedApps = FakeBlockedAppRepository(
        listOf(BlockedApp("com.example.social", "Social", enabled = true)),
    )
    private val settingsRepository = FakeSettingsRepository()
    private val alarms = FakeSessionAlarmScheduler()
    private val service = FakeZenServiceController()
    private val notifier = FakeZenNotifier()

    private lateinit var manager: ZenModeManager

    @Before
    fun setUp() {
        val stateMachine = ZenSessionStateMachine()
        val timer = ZenTimer(clock)
        val getActiveSession = GetActiveSessionUseCase(zenModeRepository)
        val getSettings = GetSettingsUseCase(settingsRepository)
        val complete = CompleteZenSessionUseCase(zenModeRepository, stateMachine, timer, clock)

        manager = ZenModeManager(
            startZenSession = StartZenSessionUseCase(
                zenModeRepository,
                blockedApps,
                stateMachine,
                clock,
            ),
            stopZenSession = StopZenSessionUseCase(
                zenModeRepository,
                complete,
                stateMachine,
                clock,
            ),
            completeZenSession = complete,
            getActiveSession = getActiveSession,
            getSettings = getSettings,
            sessionRepository = sessionRepository,
            alarmScheduler = alarms,
            serviceController = service,
            notifier = notifier,
        )
    }

    // ---- starting ----

    @Test
    fun `starting a session schedules the alarm and starts the service`() = runTest {
        val outcome = manager.startSession(25)

        assertTrue(outcome is ZenStartOutcome.Started)
        val session = (outcome as ZenStartOutcome.Started).session
        assertEquals(session.id, alarms.scheduledSessionId)
        assertEquals(session.scheduledEndAt, alarms.scheduledAt)
        assertTrue(service.running)
    }

    @Test
    fun `the alarm is set for the derived end time, not a counted-down one`() = runTest {
        manager.startSession(60)

        assertEquals(1_000_000L + 3_600_000L, alarms.scheduledAt)
    }

    @Test
    fun `an invalid duration starts nothing at all`() = runTest {
        val outcome = manager.startSession(0)

        assertTrue(outcome is ZenStartOutcome.Rejected)
        assertFalse(alarms.hasPendingAlarm)
        assertFalse(service.running)
        assertTrue(store.sessions.value.isEmpty())
    }

    @Test
    fun `a refused foreground service rolls the session back completely`() = runTest {
        service.startSucceeds = false

        val outcome = manager.startSession(25)

        assertTrue(outcome is ZenStartOutcome.Rejected)
        // No half-started session: no row, no alarm, no service.
        assertTrue(store.sessions.value.isEmpty())
        assertFalse(alarms.hasPendingAlarm)
        assertFalse(service.running)
        assertNull(manager.activeSession())
    }

    @Test
    fun `a rolled back session leaves no trace in history`() = runTest {
        service.startSucceeds = false

        manager.startSession(25)

        // Deleted rather than cancelled: the user never actually ran it.
        assertTrue(sessionRepository.observeSessions().first().isEmpty())
    }

    @Test
    fun `starting while a session runs returns the running one and keeps state`() = runTest {
        val first = (manager.startSession(25) as ZenStartOutcome.Started).session
        clock.advanceSeconds(60)

        val second = manager.startSession(45)

        assertTrue(second is ZenStartOutcome.AlreadyActive)
        assertEquals(first.id, (second as ZenStartOutcome.AlreadyActive).session.id)
        assertEquals(first.id, alarms.scheduledSessionId)
        assertEquals(1, store.sessions.value.size)
    }

    @Test
    fun `starting clears a leftover completion notification`() = runTest {
        manager.startSession(25)

        assertEquals(1, notifier.cancelCount)
    }

    // ---- stopping ----

    @Test
    fun `stopping cancels the alarm and stops the service`() = runTest {
        manager.startSession(60)
        clock.advanceSeconds(600)

        val outcome = manager.stopSession()

        assertTrue(outcome is ZenStopOutcome.Cancelled)
        assertFalse(alarms.hasPendingAlarm)
        assertFalse(service.running)
        assertNull(manager.activeSession())
    }

    @Test
    fun `a cancelled session posts no completion notification`() = runTest {
        manager.startSession(60)
        clock.advanceSeconds(600)

        manager.stopSession()

        assertEquals(0, notifier.completionsPosted)
        assertEquals(
            SessionStatus.CANCELLED,
            store.sessions.value.single().status,
        )
    }

    @Test
    fun `stopping after the time is up completes and notifies`() = runTest {
        manager.startSession(25)
        clock.advanceSeconds(1_500)

        val outcome = manager.stopSession()

        assertTrue(outcome is ZenStopOutcome.Completed)
        assertEquals(1, notifier.completionsPosted)
        assertEquals(1_500L, notifier.lastFocusedSeconds)
        assertFalse(service.running)
    }

    @Test
    fun `stopping with nothing running still leaves the system clean`() = runTest {
        val outcome = manager.stopSession()

        assertEquals(ZenStopOutcome.NoActiveSession, outcome)
        assertFalse(alarms.hasPendingAlarm)
        assertFalse(service.running)
    }

    // ---- completion and stale alarms ----

    @Test
    fun `an expired session completes and tears the system down`() = runTest {
        val session = (manager.startSession(25) as ZenStartOutcome.Started).session
        clock.advanceSeconds(1_500)

        val outcome = manager.completeIfDue(expectedSessionId = session.id)

        assertTrue(outcome is ZenStopOutcome.Completed)
        assertFalse(alarms.hasPendingAlarm)
        assertFalse(service.running)
        assertEquals(1, notifier.completionsPosted)
    }

    @Test
    fun `an alarm that fires early reschedules instead of ending the session`() = runTest {
        val session = (manager.startSession(25) as ZenStartOutcome.Started).session
        clock.advanceSeconds(900)

        manager.completeIfDue(expectedSessionId = session.id)

        assertEquals(SessionStatus.ACTIVE, manager.activeSession()?.status)
        assertTrue(alarms.hasPendingAlarm)
        assertEquals(session.scheduledEndAt, alarms.scheduledAt)
    }

    @Test
    fun `a stale alarm from a cancelled session never ends the session running now`() = runTest {
        // Session A starts and is cancelled by the user.
        val sessionA = (manager.startSession(25) as ZenStartOutcome.Started).session
        clock.advanceSeconds(60)
        manager.stopSession()

        // Session B starts.
        val sessionB = (manager.startSession(60) as ZenStartOutcome.Started).session
        clock.advanceSeconds(60)

        // A's alarm finally fires, late.
        val outcome = manager.completeIfDue(expectedSessionId = sessionA.id)

        assertEquals(ZenStopOutcome.NoActiveSession, outcome)
        assertEquals(sessionB.id, manager.activeSession()?.id)
        assertEquals(SessionStatus.ACTIVE, manager.activeSession()?.status)
        assertTrue(service.running)
        assertEquals(sessionB.id, alarms.scheduledSessionId)
        assertEquals(0, notifier.completionsPosted)
    }

    @Test
    fun `a stale alarm does not stop the service of the running session`() = runTest {
        val sessionA = (manager.startSession(25) as ZenStartOutcome.Started).session
        manager.stopSession()
        manager.startSession(60)

        manager.completeIfDue(expectedSessionId = sessionA.id)

        assertTrue(service.running)
    }

    @Test
    fun `an alarm with no session left cleans up rather than crashing`() = runTest {
        val outcome = manager.completeIfDue(expectedSessionId = 999L)

        assertEquals(ZenStopOutcome.NoActiveSession, outcome)
        assertFalse(service.running)
    }

    @Test
    fun `completion is written once even if the alarm fires twice`() = runTest {
        val session = (manager.startSession(25) as ZenStartOutcome.Started).session
        clock.advanceSeconds(1_500)

        manager.completeIfDue(expectedSessionId = session.id)
        manager.completeIfDue(expectedSessionId = session.id)

        assertEquals(1, store.sessions.value.count { it.status == SessionStatus.COMPLETED })
        assertEquals(1, notifier.completionsPosted)
    }

    @Test
    fun `the completion notification is skipped when the user turned it off`() = runTest {
        settingsRepository.setCompletionNotification(false)
        val session = (manager.startSession(25) as ZenStartOutcome.Started).session
        clock.advanceSeconds(1_500)

        manager.completeIfDue(expectedSessionId = session.id)

        assertEquals(0, notifier.completionsPosted)
    }

    // ---- recovery ----

    @Test
    fun `recovery completes a session that expired while the app was gone`() = runTest {
        manager.startSession(25)
        // Simulate the process being killed: nothing but the database survives.
        alarms.cancel()
        service.stopSessionService()
        clock.advanceSeconds(1_500 + 3_600)

        val outcome = manager.recover()

        assertTrue(outcome is ZenStopOutcome.Completed)
        // Capped at the planned duration, not the hours the device slept.
        assertEquals(1_500L, notifier.lastFocusedSeconds)
        assertFalse(service.running)
        assertFalse(alarms.hasPendingAlarm)
    }

    @Test
    fun `recovery restores the service and alarm for a session still running`() = runTest {
        val session = (manager.startSession(60) as ZenStartOutcome.Started).session
        alarms.cancel()
        service.stopSessionService()
        clock.advanceSeconds(600)

        val outcome = manager.recover()

        assertEquals(ZenStopOutcome.NoActiveSession, outcome)
        assertEquals(session.id, manager.activeSession()?.id)
        assertEquals(session.id, alarms.scheduledSessionId)
        assertEquals(session.scheduledEndAt, alarms.scheduledAt)
        assertTrue(service.running)
    }

    @Test
    fun `recovery keeps the session when Android refuses the service`() = runTest {
        val session = (manager.startSession(60) as ZenStartOutcome.Started).session
        alarms.cancel()
        service.stopSessionService()
        service.startSucceeds = false

        manager.recover()

        // The session is not cancelled just because the service could not start;
        // the database keeps the truth and the alarm still ends it.
        assertEquals(session.id, manager.activeSession()?.id)
        assertEquals(session.id, alarms.scheduledSessionId)
        assertFalse(service.running)
    }

    @Test
    fun `recovery with no session tears down anything left running`() = runTest {
        service.startSessionService()

        val outcome = manager.recover()

        assertEquals(ZenStopOutcome.NoActiveSession, outcome)
        assertFalse(service.running)
        assertFalse(alarms.hasPendingAlarm)
    }

    @Test
    fun `recovery is safe to run repeatedly`() = runTest {
        manager.startSession(60)

        manager.recover()
        manager.recover()
        manager.recover()

        assertEquals(1, store.sessions.value.size)
        assertEquals(SessionStatus.ACTIVE, manager.activeSession()?.status)
    }

    @Test
    fun `the active session is observable straight from the database`() = runTest {
        assertNull(manager.observeActiveSession().first())

        val session = (manager.startSession(25) as ZenStartOutcome.Started).session

        assertEquals(session.id, manager.observeActiveSession().first()?.id)
    }

    @Test
    fun `an inexact alarm is reported as inexact, never as exact`() = runTest {
        alarms.setExactAllowed(false)

        val outcome = manager.startSession(25) as ZenStartOutcome.Started

        assertEquals(AlarmPrecision.INEXACT, outcome.alarmPrecision)
    }

    @Test
    fun `the recorded blocked app count comes from the user's selection`() = runTest {
        val outcome = manager.startSession(25) as ZenStartOutcome.Started

        assertEquals(1, outcome.session.blockedAppCount)
    }

    @Test
    fun `settings are respected without being cached`() = runTest {
        settingsRepository.setCompletionNotification(true)
        val session = (manager.startSession(25) as ZenStartOutcome.Started).session
        settingsRepository.setCompletionNotification(false)
        clock.advanceSeconds(1_500)

        manager.completeIfDue(expectedSessionId = session.id)

        assertEquals(0, notifier.completionsPosted)
        assertEquals(ZenSettings().copy(completionNotification = false), settingsRepository.getSettings())
    }
}
