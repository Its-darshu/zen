package com.zenmode.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenDurationTest {

    @Test
    fun `the presets match the specification`() {
        assertEquals(listOf(15, 25, 45, 60, 90, 120), ZenDuration.PRESET_MINUTES)
    }

    @Test
    fun `every preset is a valid duration`() {
        ZenDuration.PRESET_MINUTES.forEach { minutes ->
            assertTrue("$minutes should be valid", ZenDuration.validate(minutes).isValid)
        }
    }

    @Test
    fun `zero and negative durations are rejected`() {
        assertEquals(DurationValidation.NotPositive, ZenDuration.validate(0))
        assertEquals(DurationValidation.NotPositive, ZenDuration.validate(-1))
        assertEquals(DurationValidation.NotPositive, ZenDuration.validate(-600))
        assertFalse(ZenDuration.validate(0).isValid)
    }

    @Test
    fun `a duration beyond the maximum is rejected`() {
        val result = ZenDuration.validate(ZenDuration.MAX_MINUTES + 1)

        assertEquals(DurationValidation.TooLong(ZenDuration.MAX_MINUTES), result)
        assertFalse(result.isValid)
    }

    @Test
    fun `the boundaries themselves are accepted`() {
        assertTrue(ZenDuration.validate(ZenDuration.MIN_MINUTES).isValid)
        assertTrue(ZenDuration.validate(ZenDuration.MAX_MINUTES).isValid)
    }

    @Test
    fun `minutes convert to seconds without overflowing`() {
        assertEquals(1_500L, ZenDuration.minutesToSeconds(25))
        assertEquals(43_200L, ZenDuration.minutesToSeconds(ZenDuration.MAX_MINUTES))
    }
}
