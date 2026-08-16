package com.zenmode.app.domain.logic

import javax.inject.Inject

/** What the blocker should do about a foreground app. */
enum class BlockingDecision {
    /** Leave it alone. */
    ALLOW,

    /** Send the user back to the Zen screen. */
    REDIRECT,
}

/**
 * Decides whether a foreground package should be interrupted
 * (specification §4, §6).
 *
 * Pure logic, no Android: the accessibility service supplies the package name
 * and the sets, and this decides. That makes every rule below testable, and
 * keeps the rules in one place instead of scattered through an event handler.
 *
 * The order of the checks matters. Exemptions are applied *before* anything
 * else, so no combination of settings can lock the user out of the dialer,
 * Android's settings, or the means of switching the service off.
 */
class BlockingDecider @Inject constructor() {

    /**
     * @param blockEverything strict mode. Instead of only the apps the user
     *   picked, every app that is not exempt is interrupted — including the
     *   launcher, which is what stops Home from being a way out of a session.
     *   The exempt set still wins, so Settings and the dialer stay reachable.
     */
    fun decide(
        packageName: String?,
        sessionActive: Boolean,
        blockedPackages: Set<String>,
        exemptPackages: Set<String>,
        blockEverything: Boolean = false,
    ): BlockingDecision {
        // No session, nothing to enforce. This is the common case: outside a
        // session the app must not interfere with anything at all.
        if (!sessionActive) return BlockingDecision.ALLOW

        if (packageName.isNullOrBlank()) return BlockingDecision.ALLOW

        // Zen Mode itself, the dialer, Android settings, the system UI and the
        // keyboard are never interrupted, in any mode, whatever any list says.
        // This is what keeps a session recoverable.
        if (packageName in exemptPackages) return BlockingDecision.ALLOW

        if (blockEverything) return BlockingDecision.REDIRECT

        if (packageName !in blockedPackages) return BlockingDecision.ALLOW

        return BlockingDecision.REDIRECT
    }
}
