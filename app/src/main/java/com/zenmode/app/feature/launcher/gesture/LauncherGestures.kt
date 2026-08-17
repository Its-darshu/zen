package com.zenmode.app.feature.launcher.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The screen regions Android has reserved for its own navigation.
 *
 * Read from the platform rather than guessed, because the size of the back
 * strip changes with the device, the navigation mode and the user's own
 * sensitivity setting.
 */
data class ReservedEdges(
    val leftPx: Float = 0f,
    val rightPx: Float = 0f,
    val bottomPx: Float = 0f,
)

/**
 * Launcher gesture rules, kept as plain arithmetic so they can be tested
 * without a device or a running Compose tree.
 *
 * Two rules matter, and both are about *not* misbehaving:
 *
 * - a gesture that starts inside a reserved edge is ignored outright, so the
 *   system Back and Home gestures keep working exactly as Android intends;
 * - a swipe fires **once** per gesture, when the accumulated distance passes a
 *   threshold — not on every frame of the drag, which is what makes a launcher
 *   feel twitchy and open the drawer by accident.
 */
object LauncherGestureRules {

    /**
     * How far a swipe must travel before it counts. Roughly a thumb's length of
     * deliberate movement: far enough that a small flick while tapping does not
     * open the drawer, short enough to feel immediate.
     */
    val SwipeThreshold = 72.dp

    /**
     * A conservative minimum for the reserved edges, used when the platform
     * reports none — a three-button device still has a bottom bar worth
     * avoiding, and an OEM may report zero while still claiming the edge.
     */
    val MinimumReservedEdge = 16.dp

    /** True when a gesture beginning here belongs to Android, not to us. */
    fun startsInReservedEdge(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        edges: ReservedEdges,
    ): Boolean = x <= edges.leftPx ||
        x >= width - edges.rightPx ||
        y >= height - edges.bottomPx

    /** True when an upward drag has travelled far enough to count. */
    fun isSwipeUp(accumulatedY: Float, thresholdPx: Float): Boolean =
        accumulatedY <= -thresholdPx

    /** True when a downward drag has travelled far enough to count. */
    fun isSwipeDown(accumulatedY: Float, thresholdPx: Float): Boolean =
        accumulatedY >= thresholdPx
}

/** Reads Android's reserved gesture regions for the current device and mode. */
@Composable
fun rememberReservedEdges(): ReservedEdges {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    val left = insets.getLeft(density, layoutDirection)
    val right = insets.getRight(density, layoutDirection)
    val bottom = insets.getBottom(density)

    return remember(left, right, bottom, density) {
        val minimum = with(density) { LauncherGestureRules.MinimumReservedEdge.toPx() }
        ReservedEdges(
            leftPx = maxOf(left.toFloat(), minimum),
            rightPx = maxOf(right.toFloat(), minimum),
            bottomPx = maxOf(bottom.toFloat(), minimum),
        )
    }
}

/**
 * A single upward swipe, reported once per gesture.
 *
 * Touch slop is honoured before anything accumulates, so this never competes
 * with a tap. Gestures that begin in a reserved edge are left entirely alone —
 * the events are not even observed, let alone consumed.
 *
 * This owns gestures *inside the launcher's own UI only*. It does not, and
 * cannot, intercept the system Back, Home or Recents gestures.
 */
fun Modifier.launcherSwipeUp(
    enabled: Boolean,
    reservedEdges: ReservedEdges,
    thresholdPx: Float,
    onSwipeUp: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(reservedEdges, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            if (
                LauncherGestureRules.startsInReservedEdge(
                    x = down.position.x,
                    y = down.position.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    edges = reservedEdges,
                )
            ) {
                // Android's territory. Do nothing at all.
                return@awaitEachGesture
            }

            var accumulated = 0f
            var fired = false

            val afterSlop = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                accumulated += overSlop
                change.consume()
            } ?: return@awaitEachGesture

            if (LauncherGestureRules.isSwipeUp(accumulated, thresholdPx)) {
                fired = true
                onSwipeUp()
            }

            verticalDrag(afterSlop.id) { change ->
                accumulated += change.positionChange().y
                if (!fired && LauncherGestureRules.isSwipeUp(accumulated, thresholdPx)) {
                    // Once per gesture, never once per frame.
                    fired = true
                    onSwipeUp()
                }
                change.consume()
            }
        }
    }
}

/**
 * A long press on empty launcher space.
 *
 * Presses that begin in a reserved edge are ignored for the same reason as
 * swipes. Every action reachable this way also has a visible control, so
 * nothing is gesture-only.
 */
fun Modifier.launcherLongPress(
    enabled: Boolean,
    reservedEdges: ReservedEdges,
    onLongPress: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(reservedEdges) {
        detectTapGestures(
            onLongPress = { offset ->
                val reserved = LauncherGestureRules.startsInReservedEdge(
                    x = offset.x,
                    y = offset.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    edges = reservedEdges,
                )
                if (!reserved) onLongPress()
            },
        )
    }
}
