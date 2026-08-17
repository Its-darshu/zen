package com.zenmode.app.feature.launcher.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules that decide whether the launcher may act on a touch.
 *
 * Both are about restraint: leaving Android's reserved edges alone, and firing
 * a swipe once rather than on every frame of a drag.
 */
class LauncherGestureRulesTest {

    private val width = 1080f
    private val height = 2400f
    private val edges = ReservedEdges(leftPx = 40f, rightPx = 40f, bottomPx = 60f)

    private fun startsInEdge(x: Float, y: Float) =
        LauncherGestureRules.startsInReservedEdge(x, y, width, height, edges)

    // ---- reserved edges ----

    @Test
    fun `a touch in the middle of the screen belongs to the launcher`() {
        assertFalse(startsInEdge(540f, 1200f))
    }

    @Test
    fun `the left edge belongs to Android's back gesture`() {
        assertTrue(startsInEdge(0f, 1200f))
        assertTrue(startsInEdge(39f, 1200f))
    }

    @Test
    fun `the right edge belongs to Android's back gesture`() {
        assertTrue(startsInEdge(width, 1200f))
        assertTrue(startsInEdge(width - 1f, 1200f))
    }

    @Test
    fun `the bottom edge belongs to Android's home gesture`() {
        assertTrue(startsInEdge(540f, height))
        assertTrue(startsInEdge(540f, height - 1f))
    }

    @Test
    fun `just inside an edge is already the launcher's`() {
        assertFalse(startsInEdge(41f, 1200f))
        assertFalse(startsInEdge(width - 41f, 1200f))
        assertFalse(startsInEdge(540f, height - 61f))
    }

    @Test
    fun `a device reporting no reserved edges still yields the exact boundary`() {
        val none = ReservedEdges()

        // With no insets only the outermost pixel row/column is Android's.
        assertFalse(LauncherGestureRules.startsInReservedEdge(1f, 1f, width, height, none))
        assertTrue(LauncherGestureRules.startsInReservedEdge(0f, 1f, width, height, none))
    }

    // ---- swipe thresholds ----

    @Test
    fun `a short drag is not a swipe`() {
        assertFalse(LauncherGestureRules.isSwipeUp(-40f, thresholdPx = 200f))
        assertFalse(LauncherGestureRules.isSwipeDown(40f, thresholdPx = 200f))
    }

    @Test
    fun `a drag past the threshold is a swipe`() {
        assertTrue(LauncherGestureRules.isSwipeUp(-200f, thresholdPx = 200f))
        assertTrue(LauncherGestureRules.isSwipeUp(-500f, thresholdPx = 200f))
    }

    @Test
    fun `direction is respected, so a downward drag never opens the drawer`() {
        assertFalse(LauncherGestureRules.isSwipeUp(500f, thresholdPx = 200f))
        assertTrue(LauncherGestureRules.isSwipeDown(500f, thresholdPx = 200f))
    }

    @Test
    fun `a drag that reverses and nets out short is not a swipe`() {
        // Accumulated, not per-frame: down 300 then up 250 nets -(-50) = 50.
        val accumulated = 300f - 250f

        assertFalse(LauncherGestureRules.isSwipeUp(accumulated, thresholdPx = 200f))
    }

    @Test
    fun `no movement is never a swipe in either direction`() {
        assertFalse(LauncherGestureRules.isSwipeUp(0f, thresholdPx = 200f))
        assertFalse(LauncherGestureRules.isSwipeDown(0f, thresholdPx = 200f))
    }

    @Test
    fun `the threshold is a deliberate distance, not a twitch`() {
        // A few pixels of movement while tapping must never count.
        assertTrue(LauncherGestureRules.SwipeThreshold.value >= 48f)
    }
}
