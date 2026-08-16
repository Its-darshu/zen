package com.zenmode.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rules the accessibility service enforces (specification §4, §6). */
class BlockingDeciderTest {

    private val decider = BlockingDecider()

    private val blocked = setOf("com.example.social", "com.example.video")
    private val exempt = setOf(
        "com.zenmode.app",
        "com.android.dialer",
        "com.android.settings",
        "com.android.systemui",
        "com.example.launcher",
    )

    private fun decide(
        packageName: String?,
        sessionActive: Boolean = true,
    ) = decider.decide(packageName, sessionActive, blocked, exempt)

    @Test
    fun `a blocked app during a session is redirected`() {
        assertEquals(BlockingDecision.REDIRECT, decide("com.example.social"))
    }

    @Test
    fun `an app the user did not block is left alone`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.example.notes"))
    }

    @Test
    fun `nothing is touched when no session is running`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.example.social", sessionActive = false))
    }

    @Test
    fun `Zen Mode never blocks itself`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.zenmode.app"))
    }

    @Test
    fun `the dialer is never blocked, so calls always work`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.android.dialer"))
    }

    @Test
    fun `Android settings are never blocked, so the service can always be turned off`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.android.settings"))
    }

    @Test
    fun `the system UI is never blocked`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.android.systemui"))
    }

    @Test
    fun `the launcher is never blocked`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.example.launcher"))
    }

    @Test
    fun `an exemption wins even if the package is somehow on the blocklist`() {
        val decision = decider.decide(
            packageName = "com.android.settings",
            sessionActive = true,
            blockedPackages = blocked + "com.android.settings",
            exemptPackages = exempt,
        )

        assertEquals(BlockingDecision.ALLOW, decision)
    }

    @Test
    fun `a missing package name is never acted on`() {
        assertEquals(BlockingDecision.ALLOW, decide(null))
        assertEquals(BlockingDecision.ALLOW, decide(""))
        assertEquals(BlockingDecision.ALLOW, decide("   "))
    }

    @Test
    fun `an empty blocklist blocks nothing`() {
        val decision = decider.decide(
            packageName = "com.example.social",
            sessionActive = true,
            blockedPackages = emptySet(),
            exemptPackages = exempt,
        )

        assertEquals(BlockingDecision.ALLOW, decision)
    }

    @Test
    fun `blocking is exact, never by prefix`() {
        assertEquals(BlockingDecision.ALLOW, decide("com.example.social.lite"))
        assertEquals(BlockingDecision.ALLOW, decide("com.example"))
    }

    // ---- strict mode ----

    @Test
    fun `strict mode blocks an app the user never put on the list`() {
        val decision = decider.decide(
            packageName = "com.example.notes",
            sessionActive = true,
            blockedPackages = emptySet(),
            exemptPackages = exempt,
            blockEverything = true,
        )

        assertEquals(BlockingDecision.REDIRECT, decision)
    }

    @Test
    fun `strict mode blocks the launcher, so Home stops being a way out`() {
        // In strict mode the launcher is not in the essential set.
        val essential = exempt - "com.example.launcher"

        val decision = decider.decide(
            packageName = "com.example.launcher",
            sessionActive = true,
            blockedPackages = emptySet(),
            exemptPackages = essential,
            blockEverything = true,
        )

        assertEquals(BlockingDecision.REDIRECT, decision)
    }

    @Test
    fun `strict mode never blocks the dialer, Settings, system UI or Zen itself`() {
        val essential = exempt - "com.example.launcher"

        listOf(
            "com.zenmode.app",
            "com.android.dialer",
            "com.android.settings",
            "com.android.systemui",
        ).forEach { pkg ->
            val decision = decider.decide(
                packageName = pkg,
                sessionActive = true,
                blockedPackages = emptySet(),
                exemptPackages = essential,
                blockEverything = true,
            )
            assertEquals("$pkg must stay reachable", BlockingDecision.ALLOW, decision)
        }
    }

    @Test
    fun `strict mode still does nothing when no session is running`() {
        val decision = decider.decide(
            packageName = "com.example.notes",
            sessionActive = false,
            blockedPackages = emptySet(),
            exemptPackages = exempt,
            blockEverything = true,
        )

        assertEquals(BlockingDecision.ALLOW, decision)
    }

    @Test
    fun `normal mode leaves unlisted apps alone`() {
        val decision = decider.decide(
            packageName = "com.example.notes",
            sessionActive = true,
            blockedPackages = blocked,
            exemptPackages = exempt,
            blockEverything = false,
        )

        assertEquals(BlockingDecision.ALLOW, decision)
    }

    @Test
    fun `every blocked app is caught, not just the first`() {
        assertEquals(BlockingDecision.REDIRECT, decide("com.example.social"))
        assertEquals(BlockingDecision.REDIRECT, decide("com.example.video"))
    }
}
