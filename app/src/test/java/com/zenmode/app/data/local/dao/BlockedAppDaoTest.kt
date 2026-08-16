package com.zenmode.app.data.local.dao

import com.zenmode.app.data.local.database.ZenDatabase
import com.zenmode.app.data.local.entity.BlockedAppEntity
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
class BlockedAppDaoTest {

    private lateinit var database: ZenDatabase
    private lateinit var dao: BlockedAppDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        dao = database.blockedAppDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun app(packageName: String, appName: String, enabled: Boolean = true) =
        BlockedAppEntity(packageName = packageName, appName = appName, enabled = enabled)

    @Test
    fun `upsert inserts then replaces the same package`() = runTest {
        dao.upsert(app("com.example.social", "Social", enabled = true))
        dao.upsert(app("com.example.social", "Social Renamed", enabled = false))

        val stored = dao.observeAll().first()

        assertEquals(1, stored.size)
        assertEquals("Social Renamed", stored.single().appName)
        assertEquals(false, stored.single().enabled)
    }

    @Test
    fun `observeAll sorts by name ignoring case`() = runTest {
        dao.upsertAll(
            listOf(
                app("com.b", "banana"),
                app("com.a", "Apple"),
                app("com.c", "Cherry"),
            ),
        )

        val names = dao.observeAll().first().map { it.appName }

        assertEquals(listOf("Apple", "banana", "Cherry"), names)
    }

    @Test
    fun `only enabled packages are reported as blocked`() = runTest {
        dao.upsertAll(
            listOf(
                app("com.a", "A", enabled = true),
                app("com.b", "B", enabled = false),
                app("com.c", "C", enabled = true),
            ),
        )

        assertEquals(setOf("com.a", "com.c"), dao.observeEnabledPackages().first().toSet())
        assertEquals(setOf("com.a", "com.c"), dao.getEnabledPackages().toSet())
        assertEquals(2, dao.countEnabled())
    }

    @Test
    fun `disableAll keeps the rows but blocks nothing`() = runTest {
        dao.upsertAll(listOf(app("com.a", "A"), app("com.b", "B")))

        dao.disableAll()

        assertEquals(2, dao.observeAll().first().size)
        assertEquals(0, dao.countEnabled())
    }

    @Test
    fun `deleteNotIn drops apps that are no longer installed`() = runTest {
        dao.upsertAll(listOf(app("com.a", "A"), app("com.gone", "Gone"), app("com.b", "B")))

        dao.deleteNotIn(listOf("com.a", "com.b"))

        assertEquals(setOf("com.a", "com.b"), dao.observeAll().first().map { it.packageName }.toSet())
    }

    @Test
    fun `deleteAll empties the blocklist`() = runTest {
        dao.upsertAll(listOf(app("com.a", "A"), app("com.b", "B")))

        dao.deleteAll()

        assertTrue(dao.observeAll().first().isEmpty())
    }
}
