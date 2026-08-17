package com.zenmode.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zenmode.app.data.local.datastore.WallpaperSettingsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Wallpaper choices against a real DataStore. Persistence is the whole point —
 * the wallpaper has to survive a reboot — so a stubbed store would test nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: WallpaperRepositoryImpl

    private val imageUri = "content://media/external/images/media/42"

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "wallpaper.preferences_pb") },
        )
        repository = WallpaperRepositoryImpl(WallpaperSettingsDataSource(dataStore))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a fresh install has no wallpaper and a black background`() = runTest {
        val settings = repository.getSettings()

        assertFalse(settings.homeEnabled)
        assertNull(settings.homeUri)
        assertFalse(settings.hasHomeWallpaper)
        assertFalse(settings.hasLockWallpaper)
    }

    @Test
    fun `choosing a home image stores it and switches it on`() = runTest {
        repository.setHomeWallpaper(imageUri)

        val settings = repository.getSettings()
        assertTrue(settings.homeEnabled)
        assertEquals(imageUri, settings.homeUri)
        assertTrue(settings.hasHomeWallpaper)
    }

    @Test
    fun `the stored value is a reference, and it is what is read back`() = runTest {
        repository.setHomeWallpaper(imageUri)

        // Read through a second repository over the same store: this is what a
        // restart looks like.
        val afterRestart = WallpaperRepositoryImpl(WallpaperSettingsDataSource(dataStore))

        assertEquals(imageUri, afterRestart.getSettings().homeUri)
    }

    @Test
    fun `turning the home wallpaper off forgets the image too`() = runTest {
        repository.setHomeWallpaper(imageUri)

        repository.clearHomeWallpaper()

        val settings = repository.getSettings()
        assertFalse(settings.homeEnabled)
        assertNull(settings.homeUri)
    }

    @Test
    fun `an image that can no longer be opened is dropped rather than retried`() = runTest {
        repository.setHomeWallpaper(imageUri)

        repository.invalidateHomeWallpaper()

        assertFalse(repository.getSettings().hasHomeWallpaper)
        assertNull(repository.getSettings().homeUri)
    }

    @Test
    fun `home and lock wallpapers are independent`() = runTest {
        repository.setHomeWallpaper(imageUri)
        repository.setLockWallpaper("content://media/external/images/media/7")

        repository.clearHomeWallpaper()

        val settings = repository.getSettings()
        assertFalse(settings.hasHomeWallpaper)
        assertTrue(settings.hasLockWallpaper)
    }

    @Test
    fun `a blank uri is refused rather than stored`() = runTest {
        repository.setHomeWallpaper("")

        assertFalse(repository.getSettings().hasHomeWallpaper)
    }

    @Test
    fun `changes are observable`() = runTest {
        assertFalse(repository.observeSettings().first().hasHomeWallpaper)

        repository.setHomeWallpaper(imageUri)

        assertTrue(repository.observeSettings().first().hasHomeWallpaper)
    }
}
