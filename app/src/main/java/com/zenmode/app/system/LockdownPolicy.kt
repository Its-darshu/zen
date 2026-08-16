package com.zenmode.app.system

/**
 * How strongly this device can be held in a Zen session.
 *
 * Android decides this, not the app. The three values are genuinely different
 * products, and the UI is expected to say which one the user actually has.
 */
enum class LockdownCapability {
    /** Lock task is not usable here. Blocking is the accessibility redirect only. */
    UNAVAILABLE,

    /**
     * Screen pinning. Home and Recents stop leaving the app, but the user can
     * always unpin by holding Back and Overview together — Android guarantees
     * that escape and the app cannot remove it.
     */
    SCREEN_PINNING,

    /**
     * Full lock task, granted by a device owner that has allow-listed this app.
     * Home and Recents are blocked outright and the keyguard is disabled, so
     * locking and unlocking the screen returns straight to the session.
     *
     * Requires provisioning that a normal consumer phone cannot do without a
     * factory reset.
     */
    KIOSK,
}

/** What the activity should do about lock task right now. */
enum class LockdownAction {
    ENTER,
    EXIT,
    NONE,
}

/**
 * Decides whether the app should be holding the device.
 *
 * Pure logic so the rule is testable without a provisioned device: strict mode
 * is only honoured while a session is genuinely running, and only where the
 * platform offers something to honour it with.
 */
object LockdownPolicy {

    fun decide(
        sessionActive: Boolean,
        strictModeEnabled: Boolean,
        capability: LockdownCapability,
        currentlyLocked: Boolean,
    ): LockdownAction {
        val shouldHold = sessionActive &&
            strictModeEnabled &&
            capability != LockdownCapability.UNAVAILABLE

        return when {
            shouldHold && !currentlyLocked -> LockdownAction.ENTER
            // Releasing whenever the session is over is what stops a crash or a
            // forgotten flag from stranding the device in lock task.
            !shouldHold && currentlyLocked -> LockdownAction.EXIT
            else -> LockdownAction.NONE
        }
    }
}
