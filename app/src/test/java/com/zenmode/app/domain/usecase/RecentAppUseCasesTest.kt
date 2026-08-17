package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.InstalledApp
import com.zenmode.app.domain.repository.InstalledAppsRepository
import com.zenmode.app.testing.FakeRecentAppsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInstalled(private val apps: List<InstalledApp>) : InstalledAppsRepository {
    override suspend fun getSelectableApps(): List<InstalledApp> = apps
    override suspend fun getProtectedPackages(): Set<String> = emptySet()
    override suspend fun getEssentialPackages(): Set<String> = emptySet()
    override suspend fun getLaunchableApps(): List<InstalledApp> = apps
}

class RecentAppUseCasesTest {

    private val installed = listOf(
        InstalledApp("com.example.camera", "Camera", isSystemApp = false),
        InstalledApp("com.example.social", "Instagram", isSystemApp = false),
        InstalledApp("com.example.notes", "Notes", isSystemApp = false),
    )

    private val recents = FakeRecentAppsRepository()
    private val getRecentApps = GetRecentAppsUseCase(recents, FakeInstalled(installed))
    private val recordOpened = RecordAppOpenedUseCase(recents)
    private val updateRecents = UpdateRecentAppsUseCase(recents)

    @Test
    fun `nothing opened yet means an empty list`() = runTest {
        assertTrue(getRecentApps().first().isEmpty())
    }

    @Test
    fun `an opened app appears with its current name and position zero`() = runTest {
        recordOpened("com.example.social")

        val app = getRecentApps().first().single()

        assertEquals("com.example.social", app.packageName)
        assertEquals("Instagram", app.appName)
        assertEquals(0, app.position)
    }

    @Test
    fun `the most recently opened app is first`() = runTest {
        recordOpened("com.example.camera")
        recordOpened("com.example.notes")
        recordOpened("com.example.social")

        val names = getRecentApps().first().map { it.appName }

        assertEquals(listOf("Instagram", "Notes", "Camera"), names)
    }

    @Test
    fun `positions are sequential, driving the stack order`() = runTest {
        recordOpened("com.example.camera")
        recordOpened("com.example.notes")

        assertEquals(listOf(0, 1), getRecentApps().first().map { it.position })
    }

    @Test
    fun `reopening an app moves it up without duplicating it`() = runTest {
        recordOpened("com.example.camera")
        recordOpened("com.example.notes")
        recordOpened("com.example.camera")

        val apps = getRecentApps().first()

        assertEquals(2, apps.size)
        assertEquals("Camera", apps.first().appName)
    }

    @Test
    fun `an app that is no longer installed is not shown`() = runTest {
        recordOpened("com.example.uninstalled")
        recordOpened("com.example.notes")

        val apps = getRecentApps().first()

        assertEquals(listOf("Notes"), apps.map { it.appName })
    }

    @Test
    fun `names are resolved live, so a renamed app shows its new name`() = runTest {
        recordOpened("com.example.social")
        val renamed = listOf(InstalledApp("com.example.social", "Threads", isSystemApp = false))

        val apps = GetRecentAppsUseCase(recents, FakeInstalled(renamed))().first()

        assertEquals("Threads", apps.single().appName)
    }

    @Test
    fun `removing an entry takes it off the list`() = runTest {
        recordOpened("com.example.camera")
        recordOpened("com.example.notes")

        updateRecents.remove("com.example.notes")

        assertEquals(listOf("Camera"), getRecentApps().first().map { it.appName })
    }

    @Test
    fun `clearing empties the list`() = runTest {
        recordOpened("com.example.camera")
        recordOpened("com.example.notes")

        updateRecents.clear()

        assertTrue(getRecentApps().first().isEmpty())
    }

    @Test
    fun `uninstalled entries are pruned from storage`() = runTest {
        recordOpened("com.example.gone")
        recordOpened("com.example.notes")

        updateRecents.removeUninstalled(installed.map { it.packageName }.toSet())

        assertEquals(listOf("com.example.notes"), recents.getRecentPackages())
    }

    @Test
    fun `an empty installed set never wipes the list`() = runTest {
        recordOpened("com.example.notes")

        updateRecents.removeUninstalled(emptySet())

        assertEquals(listOf("com.example.notes"), recents.getRecentPackages())
    }
}
