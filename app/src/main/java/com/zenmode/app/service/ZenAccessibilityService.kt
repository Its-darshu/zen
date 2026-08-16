package com.zenmode.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.zenmode.app.MainActivity
import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.BlockingDecider
import com.zenmode.app.domain.logic.BlockingDecision
import com.zenmode.app.domain.repository.InstalledAppsRepository
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetBlockedAppsUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * The distraction blocker (specification §6).
 *
 * Scope, deliberately as narrow as the job allows:
 * - it listens for one event type, `TYPE_WINDOW_STATE_CHANGED`, and reads one
 *   field from it, the package name;
 * - `canRetrieveWindowContent` is **false** in the service config, so the
 *   service is not permitted to read screen contents, text, or anything the
 *   user types, even by accident;
 * - it does nothing at all unless a Zen session is running.
 *
 * When a blocked app comes to the front during a session, the user is brought
 * back to the Zen screen once, then a cooldown stops any further redirects
 * until they move somewhere else. Nothing is logged, stored or transmitted.
 */
@AndroidEntryPoint
class ZenAccessibilityService : AccessibilityService() {

    @Inject lateinit var getActiveSession: GetActiveSessionUseCase

    @Inject lateinit var getBlockedApps: GetBlockedAppsUseCase

    @Inject lateinit var getSettings: GetSettingsUseCase

    @Inject lateinit var installedAppsRepository: InstalledAppsRepository

    @Inject lateinit var blockingDecider: BlockingDecider

    @Inject lateinit var clock: ZenClock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Cached so the event handler stays synchronous and cheap — accessibility
     * events arrive often, and blocking that thread on a database read would
     * slow the whole device down.
     */
    @Volatile private var sessionEndsAt: Long? = null

    @Volatile private var blockedPackages: Set<String> = emptySet()

    /** Everything that stays reachable in normal mode, including the launcher. */
    @Volatile private var exemptPackages: Set<String> = emptySet()

    /** The smaller set that survives strict mode: no launcher, so Home is blocked. */
    @Volatile private var essentialPackages: Set<String> = emptySet()

    /** Strict mode blocks every app that is not essential, not just the list. */
    @Volatile private var strictMode: Boolean = false

    private var lastRedirectAt = 0L
    private var lastRedirectedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceScope.launch {
            exemptPackages = runCatching { installedAppsRepository.getProtectedPackages() }
                .getOrDefault(setOf(packageName))
            essentialPackages = runCatching { installedAppsRepository.getEssentialPackages() }
                .getOrDefault(setOf(packageName))
        }
        serviceScope.launch {
            getSettings().collectLatest { settings -> strictMode = settings.strictMode }
        }
        serviceScope.launch {
            getActiveSession().collectLatest { session -> sessionEndsAt = session?.scheduledEndAt }
        }
        serviceScope.launch {
            getBlockedApps.enabledPackages().collectLatest { packages -> blockedPackages = packages }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val foregroundPackage = event.packageName?.toString() ?: return
        val now = clock.nowMillis()

        // An expired-but-not-yet-completed session enforces nothing: the moment
        // the time is up, blocking stops, whether or not the completion has been
        // written yet.
        val sessionActive = sessionEndsAt?.let { now < it } == true

        val strict = strictMode
        val decision = blockingDecider.decide(
            packageName = foregroundPackage,
            sessionActive = sessionActive,
            blockedPackages = blockedPackages,
            // Strict mode drops the launcher from the exempt set, so Home stops
            // being a way out. Settings and the dialer stay reachable in both.
            exemptPackages = if (strict) essentialPackages else exemptPackages,
            blockEverything = strict,
        )

        if (decision != BlockingDecision.REDIRECT) {
            // Moving somewhere allowed clears the guard, so coming back to a
            // blocked app later is caught immediately rather than after a wait.
            if (foregroundPackage != packageName) lastRedirectedPackage = null
            return
        }

        if (!shouldRedirectNow(foregroundPackage, now)) return

        lastRedirectAt = now
        lastRedirectedPackage = foregroundPackage
        openZenScreen()
    }

    /**
     * The loop guard.
     *
     * A blocked app is redirected once when it comes to the front. Further
     * events from that same app — dialogs, ads, its own window changes — are
     * ignored until the cooldown passes, so the redirect cannot feed itself.
     * Switching to a *different* blocked app is caught straight away.
     */
    private fun shouldRedirectNow(foregroundPackage: String, now: Long): Boolean {
        val isSameAppAsLastRedirect = foregroundPackage == lastRedirectedPackage
        val cooldownElapsed = now - lastRedirectAt >= REDIRECT_COOLDOWN_MILLIS
        return !isSameAppAsLastRedirect || cooldownElapsed
    }

    private fun openZenScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not return to the Zen screen", it) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ZenAccessibility"

        /** Long enough to stop redirect storms, short enough to feel immediate. */
        const val REDIRECT_COOLDOWN_MILLIS = 1_500L
    }
}
