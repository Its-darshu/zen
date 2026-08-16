package com.zenmode.app.data.repository

import com.zenmode.app.data.local.database.ZenDatabase
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.testing.createInMemoryDatabase
import com.zenmode.app.testing.sessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryImplTest {

    private lateinit var database: ZenDatabase
    private lateinit var repository: SessionRepositoryImpl

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository = SessionRepositoryImpl(database.sessionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seed() {
        val dao = database.sessionDao()
        dao.insert(sessionEntity(startedAt = 1_000L, status = SessionStatus.COMPLETED))
        dao.insert(sessionEntity(startedAt = 2_000L, status = SessionStatus.CANCELLED))
        dao.insert(sessionEntity(startedAt = 3_000L, status = SessionStatus.COMPLETED))
        dao.insert(sessionEntity(startedAt = 4_000L, status = SessionStatus.ACTIVE))
    }

    @Test
    fun `the ALL filter returns every session newest first`() = runTest {
        seed()

        val sessions = repository.observeSessions(SessionFilter.ALL).first()

        assertEquals(listOf(4_000L, 3_000L, 2_000L, 1_000L), sessions.map { it.startedAt })
    }

    @Test
    fun `filters narrow history to one outcome`() = runTest {
        seed()

        val completed = repository.observeSessions(SessionFilter.COMPLETED).first()
        val cancelled = repository.observeSessions(SessionFilter.CANCELLED).first()

        assertEquals(listOf(3_000L, 1_000L), completed.map { it.startedAt })
        assertEquals(listOf(2_000L), cancelled.map { it.startedAt })
    }

    @Test
    fun `completed sessions exclude cancelled and running ones`() = runTest {
        seed()

        val completed = repository.observeCompletedSessions().first()

        assertEquals(2, completed.size)
        assertTrue(completed.all { it.status == SessionStatus.COMPLETED })
    }

    @Test
    fun `sessions can be read back for a time window`() = runTest {
        seed()

        val window = repository.observeSessionsStartedBetween(from = 2_000L, to = 4_000L).first()

        assertEquals(listOf(3_000L, 2_000L), window.map { it.startedAt })
    }

    @Test
    fun `a single session can be fetched and deleted`() = runTest {
        val id = database.sessionDao().insert(sessionEntity(startedAt = 7_000L))

        assertEquals(7_000L, repository.getSession(id)?.startedAt)

        repository.deleteSession(id)

        assertNull(repository.getSession(id))
    }

    @Test
    fun `clearing history removes everything`() = runTest {
        seed()

        repository.clearHistory()

        assertTrue(repository.observeSessions(SessionFilter.ALL).first().isEmpty())
    }
}
