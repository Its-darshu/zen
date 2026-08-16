package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.testing.FakeBlockedAppRepository
import com.zenmode.app.testing.FakeZenModeRepository
import com.zenmode.app.testing.SessionStore
import com.zenmode.app.testing.zenSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockedAppsUseCasesTest {

    private val blockedApps = FakeBlockedAppRepository(
        listOf(
            BlockedApp("com.social", "Social", enabled = true),
            BlockedApp("com.video", "Video", enabled = true),
            BlockedApp("com.news", "News", enabled = false),
        ),
    )
    private val store = SessionStore()
    private val zenMode = FakeZenModeRepository(store)

    private lateinit var getBlockedApps: GetBlockedAppsUseCase
    private lateinit var updateBlockedApps: UpdateBlockedAppsUseCase

    @Before
    fun setUp() {
        getBlockedApps = GetBlockedAppsUseCase(blockedApps)
        updateBlockedApps = UpdateBlockedAppsUseCase(blockedApps, zenMode)
    }

    @Test
    fun `the blocklist is readable in full and as the packages to intercept`() = runTest {
        assertEquals(3, getBlockedApps().first().size)
        assertEquals(setOf("com.social", "com.video"), getBlockedApps.enabledPackages().first())
        assertEquals(setOf("com.social", "com.video"), getBlockedApps.enabledPackagesNow())
        assertEquals(2, getBlockedApps.enabledCount())
    }

    @Test
    fun `toggling an app changes what is intercepted`() = runTest {
        updateBlockedApps.setBlocked("com.news", "News", enabled = true)

        assertEquals(3, getBlockedApps.enabledCount())

        updateBlockedApps.setBlocked("com.news", "News", enabled = false)

        assertEquals(2, getBlockedApps.enabledCount())
    }

    @Test
    fun `a whole selection can be applied at once`() = runTest {
        updateBlockedApps.setSelection(
            listOf(
                BlockedApp("com.social", "Social", enabled = false),
                BlockedApp("com.news", "News", enabled = true),
            ),
        )

        assertEquals(setOf("com.video", "com.news"), getBlockedApps.enabledPackagesNow())
    }

    @Test
    fun `clearing the selection blocks nothing but forgets nothing`() = runTest {
        updateBlockedApps.clearSelection()

        assertEquals(0, getBlockedApps.enabledCount())
        assertEquals(3, getBlockedApps().first().size)
    }

    @Test
    fun `uninstalled apps drop off the list`() = runTest {
        updateBlockedApps.removeUninstalled(setOf("com.social", "com.news"))

        assertEquals(
            listOf("com.news", "com.social"),
            getBlockedApps().first().map { it.packageName }.sorted(),
        )
    }

    @Test
    fun `a newly installed app is never blocked on the app's own initiative`() = runTest {
        // Syncing against a device that has a new app installed adds nothing.
        updateBlockedApps.removeUninstalled(setOf("com.social", "com.video", "com.news", "com.brandnew"))

        assertTrue(getBlockedApps().first().none { it.packageName == "com.brandnew" })
        assertEquals(2, getBlockedApps.enabledCount())
    }

    @Test
    fun `editing the list during a session keeps the session's recorded count in step`() = runTest {
        store.seed(zenSession(startedAt = 0L, status = SessionStatus.ACTIVE, blockedAppCount = 2))

        updateBlockedApps.setBlocked("com.news", "News", enabled = true)

        assertEquals(3, zenMode.getActiveSession()?.blockedAppCount)
    }

    @Test
    fun `editing the list with no session running touches nothing`() = runTest {
        updateBlockedApps.setBlocked("com.news", "News", enabled = true)

        assertTrue(store.sessions.value.isEmpty())
    }
}
