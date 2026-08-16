package com.zenmode.app.data.local.dao

import com.zenmode.app.data.local.database.ZenDatabase
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.testing.createInMemoryDatabase
import com.zenmode.app.testing.sessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionDaoTest {

    private lateinit var database: ZenDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        dao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserted session round-trips with every field intact`() = runTest {
        val id = dao.insert(
            sessionEntity(
                startedAt = 5_000L,
                endedAt = 8_000L,
                plannedDurationSeconds = 900L,
                actualDurationSeconds = 3L,
                status = SessionStatus.CANCELLED,
                blockedAppCount = 7,
            ),
        )

        assertTrue(id > 0)
        val stored = dao.getById(id)
        assertNotNull(stored)
        assertEquals(5_000L, stored!!.startedAt)
        assertEquals(8_000L, stored.endedAt)
        assertEquals(900L, stored.plannedDurationSeconds)
        assertEquals(3L, stored.actualDurationSeconds)
        assertEquals(SessionStatus.CANCELLED, stored.status)
        assertEquals(7, stored.blockedAppCount)
    }

    @Test
    fun `observeAll returns newest first`() = runTest {
        dao.insert(sessionEntity(startedAt = 1_000L))
        dao.insert(sessionEntity(startedAt = 3_000L))
        dao.insert(sessionEntity(startedAt = 2_000L))

        val startTimes = dao.observeAll().first().map { it.startedAt }

        assertEquals(listOf(3_000L, 2_000L, 1_000L), startTimes)
    }

    @Test
    fun `observeByStatus only returns matching rows`() = runTest {
        dao.insert(sessionEntity(startedAt = 1_000L, status = SessionStatus.COMPLETED))
        dao.insert(sessionEntity(startedAt = 2_000L, status = SessionStatus.CANCELLED))
        dao.insert(sessionEntity(startedAt = 3_000L, status = SessionStatus.COMPLETED))

        val completed = dao.observeByStatus(SessionStatus.COMPLETED).first()
        val cancelled = dao.observeByStatus(SessionStatus.CANCELLED).first()

        assertEquals(listOf(3_000L, 1_000L), completed.map { it.startedAt })
        assertEquals(listOf(2_000L), cancelled.map { it.startedAt })
    }

    @Test
    fun `observeStartedBetween includes the lower bound and excludes the upper`() = runTest {
        dao.insert(sessionEntity(startedAt = 999L))
        dao.insert(sessionEntity(startedAt = 1_000L))
        dao.insert(sessionEntity(startedAt = 1_500L))
        dao.insert(sessionEntity(startedAt = 2_000L))

        val inRange = dao.observeStartedBetween(from = 1_000L, to = 2_000L).first()

        assertEquals(listOf(1_500L, 1_000L), inRange.map { it.startedAt })
    }

    @Test
    fun `insertIfNoneActive refuses a second active session`() = runTest {
        val firstId = dao.insertIfNoneActive(sessionEntity(status = SessionStatus.ACTIVE))
        val secondId = dao.insertIfNoneActive(sessionEntity(status = SessionStatus.ACTIVE))

        assertNotNull(firstId)
        assertNull(secondId)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `insertIfNoneActive allows a new session once the previous one ended`() = runTest {
        dao.insert(sessionEntity(status = SessionStatus.COMPLETED))
        dao.insert(sessionEntity(status = SessionStatus.CANCELLED))

        val id = dao.insertIfNoneActive(sessionEntity(status = SessionStatus.ACTIVE))

        assertNotNull(id)
    }

    @Test
    fun `updateActive writes the transformed row`() = runTest {
        dao.insert(sessionEntity(startedAt = 1_000L, status = SessionStatus.ACTIVE))

        val updated = dao.updateActive { active ->
            active.copy(
                status = SessionStatus.COMPLETED,
                endedAt = 61_000L,
                actualDurationSeconds = 60L,
            )
        }

        assertNotNull(updated)
        assertEquals(SessionStatus.COMPLETED, updated!!.status)
        val stored = dao.getById(updated.id)!!
        assertEquals(SessionStatus.COMPLETED, stored.status)
        assertEquals(61_000L, stored.endedAt)
        assertEquals(60L, stored.actualDurationSeconds)
        assertNull(dao.getLatestByStatus(SessionStatus.ACTIVE))
    }

    @Test
    fun `updateActive returns null when nothing is running`() = runTest {
        dao.insert(sessionEntity(status = SessionStatus.COMPLETED))

        assertNull(dao.updateActive { it.copy(blockedAppCount = 99) })
    }

    @Test
    fun `observeLatestByStatus emits the active session and then null once it ends`() = runTest {
        val id = dao.insert(sessionEntity(status = SessionStatus.ACTIVE))

        assertEquals(id, dao.observeLatestByStatus(SessionStatus.ACTIVE).first()?.id)

        dao.updateActive { it.copy(status = SessionStatus.COMPLETED) }

        assertNull(dao.observeLatestByStatus(SessionStatus.ACTIVE).first())
    }

    @Test
    fun `deleteById removes only that session and deleteAll clears history`() = runTest {
        val first = dao.insert(sessionEntity(startedAt = 1_000L))
        dao.insert(sessionEntity(startedAt = 2_000L))

        dao.deleteById(first)
        assertEquals(1, dao.observeAll().first().size)

        dao.deleteAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }
}
