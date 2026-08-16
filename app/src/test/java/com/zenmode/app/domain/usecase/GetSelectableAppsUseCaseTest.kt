package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.InstalledApp
import com.zenmode.app.domain.model.SelectableApp
import com.zenmode.app.domain.repository.InstalledAppsRepository
import com.zenmode.app.testing.FakeBlockedAppRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInstalledAppsRepository(
    private val apps: List<InstalledApp>,
    private val protected: Set<String> = emptySet(),
) : InstalledAppsRepository {
    override suspend fun getSelectableApps(): List<InstalledApp> = apps
    override suspend fun getProtectedPackages(): Set<String> = protected
    override suspend fun getEssentialPackages(): Set<String> = protected
}

class GetSelectableAppsUseCaseTest {

    private val installed = listOf(
        InstalledApp("com.example.social", "Social", isSystemApp = false),
        InstalledApp("com.example.video", "Video", isSystemApp = false),
        InstalledApp("com.example.news", "News Reader", isSystemApp = false),
    )

    private fun useCase(blocked: List<BlockedApp> = emptyList()) = GetSelectableAppsUseCase(
        installedAppsRepository = FakeInstalledAppsRepository(installed),
        blockedAppRepository = FakeBlockedAppRepository(blocked),
    )

    @Test
    fun `every installed app is offered, unblocked by default`() = runTest {
        val apps = useCase()().first()

        assertEquals(3, apps.size)
        assertTrue(apps.none { it.isBlocked })
    }

    @Test
    fun `apps the user has switched on come back marked as blocked`() = runTest {
        val apps = useCase(
            listOf(BlockedApp("com.example.social", "Social", enabled = true)),
        )().first()

        assertTrue(apps.single { it.packageName == "com.example.social" }.isBlocked)
        assertFalse(apps.single { it.packageName == "com.example.video" }.isBlocked)
    }

    @Test
    fun `a stored app that is no longer installed is not listed`() = runTest {
        val apps = useCase(
            listOf(BlockedApp("com.example.uninstalled", "Gone", enabled = true)),
        )().first()

        assertTrue(apps.none { it.packageName == "com.example.uninstalled" })
    }

    @Test
    fun `the list updates when the selection changes`() = runTest {
        val blockedApps = FakeBlockedAppRepository()
        val useCase = GetSelectableAppsUseCase(FakeInstalledAppsRepository(installed), blockedApps)

        assertTrue(useCase().first().none { it.isBlocked })

        blockedApps.setBlocked("com.example.video", "Video", enabled = true)

        assertTrue(useCase().first().single { it.packageName == "com.example.video" }.isBlocked)
    }

    @Test
    fun `search matches names case-insensitively`() {
        val apps = listOf(
            SelectableApp("com.example.social", "Social", isBlocked = false),
            SelectableApp("com.example.video", "Video", isBlocked = false),
        )

        assertEquals(1, GetSelectableAppsUseCase.search(apps, "soc").size)
        assertEquals(1, GetSelectableAppsUseCase.search(apps, "SOCIAL").size)
    }

    @Test
    fun `search also matches the package name`() {
        val apps = listOf(SelectableApp("com.example.social", "Something Else", isBlocked = false))

        assertEquals(1, GetSelectableAppsUseCase.search(apps, "example.soc").size)
    }

    @Test
    fun `an empty or whitespace query matches everything`() {
        val apps = listOf(
            SelectableApp("com.a", "A", isBlocked = false),
            SelectableApp("com.b", "B", isBlocked = false),
        )

        assertEquals(2, GetSelectableAppsUseCase.search(apps, "").size)
        assertEquals(2, GetSelectableAppsUseCase.search(apps, "   ").size)
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        val apps = listOf(SelectableApp("com.a", "A", isBlocked = false))

        assertTrue(GetSelectableAppsUseCase.search(apps, "zzz").isEmpty())
    }
}
