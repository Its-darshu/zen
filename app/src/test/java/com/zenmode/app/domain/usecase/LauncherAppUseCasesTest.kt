package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.InstalledApp
import com.zenmode.app.domain.model.LauncherApp
import com.zenmode.app.domain.repository.InstalledAppsRepository
import com.zenmode.app.testing.FakeFavoriteAppsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInstalledApps(
    private val launchable: List<InstalledApp>,
) : InstalledAppsRepository {
    override suspend fun getSelectableApps(): List<InstalledApp> = launchable
    override suspend fun getProtectedPackages(): Set<String> = emptySet()
    override suspend fun getEssentialPackages(): Set<String> = emptySet()
    override suspend fun getLaunchableApps(): List<InstalledApp> = launchable
}

class LauncherAppUseCasesTest {

    private val installed = listOf(
        InstalledApp("com.example.camera", "Camera", isSystemApp = false),
        InstalledApp("com.example.social", "Instagram", isSystemApp = false),
        InstalledApp("com.example.notes", "Notes", isSystemApp = false),
    )

    private val favorites = FakeFavoriteAppsRepository()
    private val getLauncherApps = GetLauncherAppsUseCase(FakeInstalledApps(installed), favorites)
    private val updateFavorites = UpdateFavoriteAppsUseCase(favorites)

    // ---- the drawer list ----

    @Test
    fun `every launchable app is offered`() = runTest {
        val apps = getLauncherApps().first()

        assertEquals(3, apps.size)
        assertTrue(apps.none { it.isFavorite })
    }

    @Test
    fun `the drawer keeps the alphabetical order the provider gives it`() = runTest {
        val names = getLauncherApps().first().map { it.appName }

        assertEquals(listOf("Camera", "Instagram", "Notes"), names)
    }

    @Test
    fun `pinned apps come back marked`() = runTest {
        favorites.addFavorite("com.example.social")

        val apps = getLauncherApps().first()

        assertTrue(apps.single { it.packageName == "com.example.social" }.isFavorite)
        assertFalse(apps.single { it.packageName == "com.example.notes" }.isFavorite)
    }

    @Test
    fun `the list updates when a favourite changes, without rescanning`() = runTest {
        assertTrue(getLauncherApps().first().none { it.isFavorite })

        favorites.addFavorite("com.example.camera")

        assertEquals(1, getLauncherApps().first().count { it.isFavorite })
    }

    @Test
    fun `favourites are just the pinned apps`() = runTest {
        favorites.addFavorite("com.example.notes")

        val pinned = getLauncherApps.favorites().first()

        assertEquals(listOf("Notes"), pinned.map { it.appName })
    }

    @Test
    fun `a favourite for an app that is gone is not shown`() = runTest {
        favorites.addFavorite("com.example.uninstalled")

        assertTrue(getLauncherApps.favorites().first().isEmpty())
    }

    // ---- search ----

    private val searchable = listOf(
        LauncherApp("com.example.social", "Instagram"),
        LauncherApp("com.example.camera", "Camera"),
        LauncherApp("com.example.notes", "Notes"),
    )

    @Test
    fun `search matches part of a name, case-insensitively`() {
        assertEquals(
            listOf("Instagram"),
            GetLauncherAppsUseCase.search(searchable, "insta").map { it.appName },
        )
        assertEquals(
            listOf("Instagram"),
            GetLauncherAppsUseCase.search(searchable, "INSTA").map { it.appName },
        )
        assertEquals(
            listOf("Instagram"),
            GetLauncherAppsUseCase.search(searchable, "gram").map { it.appName },
        )
    }

    @Test
    fun `an empty or blank query matches everything`() {
        assertEquals(3, GetLauncherAppsUseCase.search(searchable, "").size)
        assertEquals(3, GetLauncherAppsUseCase.search(searchable, "   ").size)
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        assertTrue(GetLauncherAppsUseCase.search(searchable, "zzz").isEmpty())
    }

    @Test
    fun `search is by name, not by package`() {
        // A launcher search is for the label a person reads.
        assertTrue(GetLauncherAppsUseCase.search(searchable, "com.example").isEmpty())
    }

    // ---- favourites ----

    @Test
    fun `pinning the same app twice does not duplicate it`() = runTest {
        updateFavorites.setFavorite("com.example.notes", true)
        updateFavorites.setFavorite("com.example.notes", true)

        assertEquals(setOf("com.example.notes"), favorites.getFavorites())
    }

    @Test
    fun `unpinning removes it`() = runTest {
        updateFavorites.setFavorite("com.example.notes", true)
        updateFavorites.setFavorite("com.example.notes", false)

        assertTrue(favorites.getFavorites().isEmpty())
    }

    @Test
    fun `toggling flips whatever the current state is`() = runTest {
        updateFavorites.toggle("com.example.camera")
        assertEquals(setOf("com.example.camera"), favorites.getFavorites())

        updateFavorites.toggle("com.example.camera")
        assertTrue(favorites.getFavorites().isEmpty())
    }

    @Test
    fun `uninstalled apps are dropped from favourites`() = runTest {
        updateFavorites.setFavorite("com.example.notes", true)
        updateFavorites.setFavorite("com.example.gone", true)

        updateFavorites.removeUninstalled(installed.map { it.packageName }.toSet())

        assertEquals(setOf("com.example.notes"), favorites.getFavorites())
    }

    @Test
    fun `an empty installed list never wipes favourites`() = runTest {
        updateFavorites.setFavorite("com.example.notes", true)

        updateFavorites.removeUninstalled(emptySet())

        assertEquals(setOf("com.example.notes"), favorites.getFavorites())
    }
}
