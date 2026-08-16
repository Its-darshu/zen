package com.zenmode.app.data.repository

import com.zenmode.app.data.local.database.ZenDatabase
import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.testing.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedAppRepositoryImplTest {

    private lateinit var database: ZenDatabase
    private lateinit var repository: BlockedAppRepositoryImpl

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository = BlockedAppRepositoryImpl(database.blockedAppDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `toggling an app on and off changes what is blocked without losing the app`() = runTest {
        repository.setBlocked("com.example.social", "Social", enabled = true)

        assertEquals(setOf("com.example.social"), repository.getEnabledPackages())

        repository.setBlocked("com.example.social", "Social", enabled = false)

        assertTrue(repository.getEnabledPackages().isEmpty())
        assertEquals(1, repository.observeBlockedApps().first().size)
    }

    @Test
    fun `a whole selection can be written at once`() = runTest {
        repository.setBlockedApps(
            listOf(
                BlockedApp("com.a", "A", enabled = true),
                BlockedApp("com.b", "B", enabled = false),
                BlockedApp("com.c", "C", enabled = true),
            ),
        )

        assertEquals(setOf("com.a", "com.c"), repository.observeEnabledPackages().first())
        assertEquals(2, repository.countEnabled())
    }

    @Test
    fun `writing an empty selection is a no-op`() = runTest {
        repository.setBlockedApps(listOf(BlockedApp("com.a", "A", enabled = true)))

        repository.setBlockedApps(emptyList())

        assertEquals(setOf("com.a"), repository.getEnabledPackages())
    }

    @Test
    fun `clearing the selection switches every app off`() = runTest {
        repository.setBlockedApps(
            listOf(BlockedApp("com.a", "A", true), BlockedApp("com.b", "B", true)),
        )

        repository.clearSelection()

        assertEquals(0, repository.countEnabled())
        assertEquals(2, repository.observeBlockedApps().first().size)
    }

    @Test
    fun `uninstalled apps are dropped from the blocklist`() = runTest {
        repository.setBlockedApps(
            listOf(
                BlockedApp("com.a", "A", true),
                BlockedApp("com.gone", "Gone", true),
            ),
        )

        repository.removeUninstalled(setOf("com.a", "com.other"))

        assertEquals(listOf("com.a"), repository.observeBlockedApps().first().map { it.packageName })
    }

    @Test
    fun `an empty installed list never wipes the blocklist`() = runTest {
        repository.setBlockedApps(listOf(BlockedApp("com.a", "A", true)))

        repository.removeUninstalled(emptySet())

        assertEquals(1, repository.observeBlockedApps().first().size)
    }
}
