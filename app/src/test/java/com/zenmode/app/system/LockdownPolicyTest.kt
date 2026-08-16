package com.zenmode.app.system

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When the app asks Android to hold the device, and — more importantly — when
 * it lets go. Getting the release wrong is what would strand a device.
 */
class LockdownPolicyTest {

    private fun decide(
        sessionActive: Boolean,
        strictModeEnabled: Boolean = true,
        capability: LockdownCapability = LockdownCapability.KIOSK,
        currentlyLocked: Boolean = false,
    ) = LockdownPolicy.decide(sessionActive, strictModeEnabled, capability, currentlyLocked)

    @Test
    fun `a strict session on a capable device takes hold of the task`() {
        assertEquals(LockdownAction.ENTER, decide(sessionActive = true))
    }

    @Test
    fun `holding is not repeated once it is already in effect`() {
        assertEquals(
            LockdownAction.NONE,
            decide(sessionActive = true, currentlyLocked = true),
        )
    }

    @Test
    fun `the device is released the moment the session ends`() {
        assertEquals(
            LockdownAction.EXIT,
            decide(sessionActive = false, currentlyLocked = true),
        )
    }

    @Test
    fun `turning strict mode off mid-session releases the device`() {
        assertEquals(
            LockdownAction.EXIT,
            decide(sessionActive = true, strictModeEnabled = false, currentlyLocked = true),
        )
    }

    @Test
    fun `nothing is held when strict mode is off`() {
        assertEquals(LockdownAction.NONE, decide(sessionActive = true, strictModeEnabled = false))
    }

    @Test
    fun `nothing is held when no session is running`() {
        assertEquals(LockdownAction.NONE, decide(sessionActive = false))
    }

    @Test
    fun `a device that cannot lock the task is never asked to`() {
        assertEquals(
            LockdownAction.NONE,
            decide(sessionActive = true, capability = LockdownCapability.UNAVAILABLE),
        )
    }

    @Test
    fun `screen pinning is used where that is all the device offers`() {
        assertEquals(
            LockdownAction.ENTER,
            decide(sessionActive = true, capability = LockdownCapability.SCREEN_PINNING),
        )
    }

    @Test
    fun `an incapable device still releases anything it somehow holds`() {
        // Strict mode may have been on before the capability changed — for
        // example after device-owner status was removed.
        assertEquals(
            LockdownAction.EXIT,
            decide(
                sessionActive = false,
                capability = LockdownCapability.UNAVAILABLE,
                currentlyLocked = true,
            ),
        )
    }

    @Test
    fun `the decision is stable when called repeatedly`() {
        repeat(3) {
            assertEquals(
                LockdownAction.NONE,
                decide(sessionActive = true, currentlyLocked = true),
            )
        }
    }
}
