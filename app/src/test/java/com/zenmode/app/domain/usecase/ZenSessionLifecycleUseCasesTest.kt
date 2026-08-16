package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.logic.ZenSessionStateMachine
import com.zenmode.app.domain.logic.ZenTimer
import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.DurationValidation
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenDuration
import com.zenmode.app.testing.FakeBlockedAppRepository
import com.zenmode.app.testing.FakeZenClock
import com.zenmode.app.testing.FakeZenModeRepository
import com.zenmode.app.testing.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Start → run → finish, exercised end to end through the use cases with fake
 * repositories and a hand-driven clock.
 */
class ZenSessionLifecycleUseCasesTest {

    private val clock = FakeZenClock(now = 1_000_000L)
    private val store = SessionStore()
    private val zenModeRepository = FakeZenModeRepository(store)
    private val blockedApps = FakeBlockedAppRepository(
        listOf(
            BlockedApp("com.a", "A", enabled = true),
            BlockedApp("com.b", "B", enabled = true),
            BlockedApp("com.c", "C", enabled = false),
        ),
    )
    private val stateMachine = ZenSessionStateMachine()
    private val timer = ZenTimer(clock)

    private lateinit var startSession: StartZenSessionUseCase
    private lateinit var completeSession: CompleteZenSessionUseCase
    private lateinit var stopSession: StopZenSessionUseCase
    private lateinit var getActiveSession: GetActiveSessionUseCase

    @Before
    fun setUp() {
        startSession = StartZenSessionUseCase(zenModeRepository, blockedApps, stateMachine, clock)
        completeSession = CompleteZenSessionUseCase(zenModeRepository, stateMachine, timer, clock)
        stopSession = StopZenSessionUseCase(zenModeRepository, completeSession, stateMachine, clock)
        getActiveSession = GetActiveSessionUseCase(zenModeRepository)
    }

    // ---- starting ----

    @Test
    fun `starting a session records the time, the duration and the blocked app count`() = runTest {
        val result = startSession(25)

        assertTrue(result is StartZenSessionResult.Started)
        val session = (result as StartZenSessionResult.Started).session
        assertEquals(SessionStatus.ACTIVE, session.status)
        assertEquals(1_000_000L, session.startedAt)
        assertEquals(1_500L, session.plannedDurationSeconds)
        assertEquals(2, session.blockedAppCount)
        assertNull(session.endedAt)
    }

    @Test
    fun `a started session becomes the active session`() = runTest {
        val started = (startSession(25) as StartZenSessionResult.Started).session

        assertEquals(started, getActiveSession.current())
        assertEquals(started, getActiveSession().first())
    }

    @Test
    fun `the end time is derived, never stored as a countdown`() = runTest {
        val session = (startSession(60) as StartZenSessionResult.Started).session

        assertEquals(1_000_000L + 3_600_000L, session.scheduledEndAt)
        assertEquals(3_600L, timer.remainingSeconds(session))

        clock.advanceSeconds(600)
        assertEquals(3_000L, timer.remainingSeconds(session))
    }

    @Test
    fun `a second start while one is running is refused`() = runTest {
        val first = (startSession(25) as StartZenSessionResult.Started).session
        clock.advanceSeconds(60)

        val second = startSession(45)

        assertTrue(second is StartZenSessionResult.AlreadyActive)
        assertEquals(first.id, (second as StartZenSessionResult.AlreadyActive).session.id)
        assertEquals(1, store.sessions.value.size)
    }

    @Test
    fun `a zero or negative duration is rejected before anything is written`() = runTest {
        assertEquals(
            DurationValidation.NotPositive,
            (startSession(0) as StartZenSessionResult.InvalidDuration).validation,
        )
        assertEquals(
            DurationValidation.NotPositive,
            (startSession(-30) as StartZenSessionResult.InvalidDuration).validation,
        )
        assertTrue(store.sessions.value.isEmpty())
    }

    @Test
    fun `an unreasonably long duration is rejected`() = runTest {
        val result = startSession(ZenDuration.MAX_MINUTES + 1)

        assertEquals(
            DurationValidation.TooLong(ZenDuration.MAX_MINUTES),
            (result as StartZenSessionResult.InvalidDuration).validation,
        )
        assertTrue(store.sessions.value.isEmpty())
    }

    @Test
    fun `a session can be started with nothing blocked`() = runTest {
        val emptyBlocklist = FakeBlockedAppRepository()
        val useCase = StartZenSessionUseCase(zenModeRepository, emptyBlocklist, stateMachine, clock)

        val result = useCase(15)

        assertEquals(0, (result as StartZenSessionResult.Started).session.blockedAppCount)
    }

    // ---- completing ----

    @Test
    fun `an expired session completes and records the planned duration`() = runTest {
        startSession(25)
        clock.advanceSeconds(1_500)

        val result = completeSession()

        val session = (result as CompleteZenSessionResult.Completed).session
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(clock.now, session.endedAt)
        assertEquals(1_500L, session.actualDurationSeconds)
        assertNull(getActiveSession.current())
    }

    @Test
    fun `a session that has not run out is not completed`() = runTest {
        startSession(25)
        clock.advanceSeconds(900)

        val result = completeSession()

        assertEquals(600L, (result as CompleteZenSessionResult.NotExpired).remainingSeconds)
        assertEquals(SessionStatus.ACTIVE, getActiveSession.current()?.status)
    }

    @Test
    fun `completion is idempotent so a repeated alarm cannot write a second record`() = runTest {
        startSession(25)
        clock.advanceSeconds(1_500)

        assertTrue(completeSession() is CompleteZenSessionResult.Completed)
        assertTrue(completeSession() is CompleteZenSessionResult.NoActiveSession)
        assertEquals(1, store.sessions.value.size)
    }

    @Test
    fun `a late completion still records only the time the user asked for`() = runTest {
        startSession(25)
        // The device slept; the alarm ran an hour after the session ended.
        clock.advanceSeconds(1_500 + 3_600)

        val session = (completeSession() as CompleteZenSessionResult.Completed).session

        assertEquals(1_500L, session.actualDurationSeconds)
    }

    @Test
    fun `completing with nothing running reports no active session`() = runTest {
        assertTrue(completeSession() is CompleteZenSessionResult.NoActiveSession)
    }

    @Test
    fun `a session can be completed early only when forced`() = runTest {
        startSession(25)
        clock.advanceSeconds(300)

        val result = completeSession(force = true)

        val session = (result as CompleteZenSessionResult.Completed).session
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(300L, session.actualDurationSeconds)
    }

    // ---- stopping ----

    @Test
    fun `stopping early records a cancelled session with the time actually spent`() = runTest {
        startSession(60)
        clock.advanceSeconds(600)

        val result = stopSession()

        val session = (result as StopZenSessionResult.Cancelled).session
        assertEquals(SessionStatus.CANCELLED, session.status)
        assertEquals(600L, session.actualDurationSeconds)
        assertEquals(3_600L, session.plannedDurationSeconds)
        assertEquals(clock.now, session.endedAt)
        assertNull(getActiveSession.current())
    }

    @Test
    fun `a cancelled session is never counted as completed`() = runTest {
        startSession(60)
        clock.advanceSeconds(600)
        stopSession()

        assertTrue(store.sessions.value.none { it.status == SessionStatus.COMPLETED })
    }

    @Test
    fun `stopping after the timer already ran out completes instead of cancelling`() = runTest {
        startSession(25)
        clock.advanceSeconds(1_500 + 30)

        val result = stopSession()

        val session = (result as StopZenSessionResult.Completed).session
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(1_500L, session.actualDurationSeconds)
    }

    @Test
    fun `stopping with nothing running reports no active session`() = runTest {
        assertTrue(stopSession() is StopZenSessionResult.NoActiveSession)
    }

    @Test
    fun `a new session can be started once the previous one ended`() = runTest {
        startSession(25)
        clock.advanceSeconds(1_500)
        completeSession()

        val result = startSession(45)

        assertTrue(result is StartZenSessionResult.Started)
        assertEquals(2, store.sessions.value.size)
    }

    @Test
    fun `the session survives being read back after the process would have died`() = runTest {
        val started = (startSession(60) as StartZenSessionResult.Started).session
        clock.advanceSeconds(1_800)

        // Nothing in memory: the countdown is re-derived from the stored row.
        val recovered = GetActiveSessionUseCase(FakeZenModeRepository(store)).current()

        assertEquals(started.id, recovered?.id)
        assertEquals(1_800L, timer.remainingSeconds(recovered!!))
    }
}
