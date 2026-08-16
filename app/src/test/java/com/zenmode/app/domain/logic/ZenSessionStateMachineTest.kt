package com.zenmode.app.domain.logic

import com.zenmode.app.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenSessionStateMachineTest {

    private val machine = ZenSessionStateMachine()
    private val machineWithPause = ZenSessionStateMachine(pauseSupported = true)

    @Test
    fun `the happy path runs idle to starting to active to completing to completed`() {
        assertTrue(machine.canTransition(SessionStatus.IDLE, SessionStatus.STARTING))
        assertTrue(machine.canTransition(SessionStatus.STARTING, SessionStatus.ACTIVE))
        assertTrue(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.COMPLETING))
        assertTrue(machine.canTransition(SessionStatus.COMPLETING, SessionStatus.COMPLETED))
        assertTrue(machine.canTransition(SessionStatus.COMPLETED, SessionStatus.IDLE))
    }

    @Test
    fun `a running session can be cancelled and then acknowledged`() {
        assertTrue(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.CANCELLED))
        assertTrue(machine.canTransition(SessionStatus.CANCELLED, SessionStatus.IDLE))
    }

    @Test
    fun `a start that never gets going falls back to idle`() {
        assertTrue(machine.canTransition(SessionStatus.STARTING, SessionStatus.IDLE))
    }

    @Test
    fun `a session cannot skip the completing step`() {
        assertFalse(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.COMPLETED))
    }

    @Test
    fun `a session cannot become active without starting`() {
        assertFalse(machine.canTransition(SessionStatus.IDLE, SessionStatus.ACTIVE))
        assertFalse(machine.canTransition(SessionStatus.IDLE, SessionStatus.COMPLETED))
        assertFalse(machine.canTransition(SessionStatus.IDLE, SessionStatus.CANCELLED))
    }

    @Test
    fun `a finished session cannot come back to life`() {
        assertFalse(machine.canTransition(SessionStatus.COMPLETED, SessionStatus.ACTIVE))
        assertFalse(machine.canTransition(SessionStatus.CANCELLED, SessionStatus.ACTIVE))
        assertFalse(machine.canTransition(SessionStatus.COMPLETED, SessionStatus.CANCELLED))
        assertFalse(machine.canTransition(SessionStatus.CANCELLED, SessionStatus.COMPLETED))
        assertFalse(machine.canTransition(SessionStatus.COMPLETING, SessionStatus.ACTIVE))
    }

    @Test
    fun `no state transitions to itself`() {
        SessionStatus.entries.forEach { state ->
            assertFalse("$state → $state should be rejected", machine.canTransition(state, state))
        }
    }

    @Test
    fun `pausing is switched off for the MVP`() {
        assertFalse(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.PAUSED))
        assertFalse(machine.canTransition(SessionStatus.PAUSED, SessionStatus.ACTIVE))
        assertTrue(SessionStatus.PAUSED !in machine.allowedTransitionsFrom(SessionStatus.ACTIVE))
    }

    @Test
    fun `the paused branch exists in the model and works when switched on`() {
        assertTrue(machineWithPause.canTransition(SessionStatus.ACTIVE, SessionStatus.PAUSED))
        assertTrue(machineWithPause.canTransition(SessionStatus.PAUSED, SessionStatus.ACTIVE))
        assertTrue(machineWithPause.canTransition(SessionStatus.PAUSED, SessionStatus.CANCELLED))
        assertTrue(machineWithPause.canTransition(SessionStatus.PAUSED, SessionStatus.COMPLETING))
    }

    @Test
    fun `transition returns the new state for a legal move`() {
        assertEquals(
            SessionStatus.ACTIVE,
            machine.transition(SessionStatus.STARTING, SessionStatus.ACTIVE),
        )
    }

    @Test
    fun `transition throws on an illegal move`() {
        val error = runCatching {
            machine.transition(SessionStatus.IDLE, SessionStatus.COMPLETED)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("IDLE"))
        assertTrue(error.message!!.contains("COMPLETED"))
    }

    @Test
    fun `transitionOrNull reports an illegal move without throwing`() {
        assertNull(machine.transitionOrNull(SessionStatus.IDLE, SessionStatus.ACTIVE))
        assertEquals(
            SessionStatus.STARTING,
            machine.transitionOrNull(SessionStatus.IDLE, SessionStatus.STARTING),
        )
    }

    @Test
    fun `terminal states only lead back to idle`() {
        assertEquals(setOf(SessionStatus.IDLE), machine.allowedTransitionsFrom(SessionStatus.COMPLETED))
        assertEquals(setOf(SessionStatus.IDLE), machine.allowedTransitionsFrom(SessionStatus.CANCELLED))
    }

    @Test
    fun `only completed and cancelled are treated as terminal`() {
        assertTrue(SessionStatus.COMPLETED.isTerminal)
        assertTrue(SessionStatus.CANCELLED.isTerminal)
        assertFalse(SessionStatus.ACTIVE.isTerminal)
        assertFalse(SessionStatus.COMPLETING.isTerminal)
    }

    @Test
    fun `only active completed and cancelled are ever written to the database`() {
        assertTrue(SessionStatus.ACTIVE.isPersistable)
        assertTrue(SessionStatus.COMPLETED.isPersistable)
        assertTrue(SessionStatus.CANCELLED.isPersistable)
        assertFalse(SessionStatus.IDLE.isPersistable)
        assertFalse(SessionStatus.STARTING.isPersistable)
        assertFalse(SessionStatus.COMPLETING.isPersistable)
        assertFalse(SessionStatus.PAUSED.isPersistable)
    }
}
