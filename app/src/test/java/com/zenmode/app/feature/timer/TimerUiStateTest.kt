package com.zenmode.app.feature.timer

import com.zenmode.app.domain.model.ZenDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The duration rules the timer screen enforces (specification §14). */
class TimerUiStateTest {

    @Test
    fun `a preset is the chosen duration`() {
        val state = TimerUiState(selectedPresetMinutes = 45, isCustom = false)

        assertEquals(45, state.totalMinutes)
        assertTrue(state.canStart)
        assertNull(state.validationMessage)
    }

    @Test
    fun `a custom duration combines hours and minutes`() {
        val state = TimerUiState(isCustom = true, customHours = 1, customMinutes = 30)

        assertEquals(90, state.totalMinutes)
        assertTrue(state.canStart)
    }

    @Test
    fun `a zero custom duration cannot be started`() {
        val state = TimerUiState(isCustom = true, customHours = 0, customMinutes = 0)

        assertEquals(0, state.totalMinutes)
        assertFalse(state.canStart)
        assertNotNull(state.validationMessage)
    }

    @Test
    fun `no preset chosen yet cannot be started`() {
        val state = TimerUiState(selectedPresetMinutes = null, isCustom = false)

        assertFalse(state.canStart)
    }

    @Test
    fun `a custom duration beyond the maximum is refused`() {
        val state = TimerUiState(
            isCustom = true,
            customHours = ZenDuration.MAX_CUSTOM_HOURS + 1,
            customMinutes = 0,
        )

        assertFalse(state.canStart)
        assertEquals("The longest session is 12 hours.", state.validationMessage)
    }

    @Test
    fun `the maximum itself is allowed`() {
        val state = TimerUiState(
            isCustom = true,
            customHours = ZenDuration.MAX_CUSTOM_HOURS,
            customMinutes = 0,
        )

        assertTrue(state.canStart)
    }

    @Test
    fun `one minute is enough to start`() {
        val state = TimerUiState(isCustom = true, customHours = 0, customMinutes = 1)

        assertTrue(state.canStart)
    }

    @Test
    fun `blocking readiness needs both the permission and some apps`() {
        assertFalse(TimerUiState(accessibilityEnabled = true, blockedAppCount = 0).isSetUpForBlocking)
        assertFalse(TimerUiState(accessibilityEnabled = false, blockedAppCount = 3).isSetUpForBlocking)
        assertTrue(TimerUiState(accessibilityEnabled = true, blockedAppCount = 3).isSetUpForBlocking)
    }
}
