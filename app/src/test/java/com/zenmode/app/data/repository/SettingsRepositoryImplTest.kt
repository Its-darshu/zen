package com.zenmode.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zenmode.app.data.local.datastore.SettingsDataSource
import com.zenmode.app.domain.model.ZenSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the real DataStore against a temporary file: the serialization and
 * default-value behaviour is the part worth testing, not a stubbed store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "zen_settings.preferences_pb") },
        )
        repository = SettingsRepositoryImpl(SettingsDataSource(dataStore))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an untouched store reads back the documented defaults`() = runTest {
        val settings = repository.getSettings()

        assertEquals(ZenSettings(), settings)
        assertEquals(25, settings.defaultDurationMinutes)
        assertTrue(settings.confirmStart)
        assertFalse(settings.strictMode)
        assertTrue(settings.completionNotification)
        assertTrue(settings.pureBlackZenScreen)
        assertTrue(settings.showClock)
        assertTrue(settings.showDate)
        assertTrue(settings.use24HourClock)
        assertTrue(settings.showCallButton)
        assertFalse(settings.onboardingCompleted)
    }

    @Test
    fun `every setter persists its own value and leaves the rest alone`() = runTest {
        repository.setDefaultDurationMinutes(45)
        repository.setConfirmStart(false)
        repository.setCompletionNotification(false)
        repository.setPureBlackZenScreen(false)
        repository.setShowClock(false)
        repository.setShowDate(false)
        repository.setUse24HourClock(false)
        repository.setShowCallButton(false)
        repository.setStrictMode(true)
        repository.setOnboardingCompleted(true)

        val settings = repository.getSettings()

        assertEquals(45, settings.defaultDurationMinutes)
        assertFalse(settings.confirmStart)
        assertFalse(settings.completionNotification)
        assertFalse(settings.pureBlackZenScreen)
        assertFalse(settings.showClock)
        assertFalse(settings.showDate)
        assertFalse(settings.use24HourClock)
        assertFalse(settings.showCallButton)
        assertTrue(settings.strictMode)
        assertTrue(settings.onboardingCompleted)
    }

    @Test
    fun `changes are observable`() = runTest {
        assertEquals(25, repository.observeSettings().first().defaultDurationMinutes)

        repository.setDefaultDurationMinutes(90)

        assertEquals(90, repository.observeSettings().first().defaultDurationMinutes)
    }

    @Test
    fun `resetting restores defaults but does not replay onboarding`() = runTest {
        repository.setDefaultDurationMinutes(120)
        repository.setShowDate(false)
        repository.setOnboardingCompleted(true)

        repository.resetToDefaults()

        val settings = repository.getSettings()
        assertEquals(25, settings.defaultDurationMinutes)
        assertTrue(settings.showDate)
        assertTrue(settings.onboardingCompleted)
    }
}
