package com.zenmode.app.data.repository

import com.zenmode.app.data.local.database.ZenDatabase
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.testing.createInMemoryDatabase
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
class ZenModeRepositoryImplTest {

    private lateinit var database: ZenDatabase
    private lateinit var repository: ZenModeRepositoryImpl

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository = ZenModeRepositoryImpl(database.sessionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun activeSession(
        startedAt: Long = 1_000L,
        plannedDurationSeconds: Long = 1_500L,
        blockedAppCount: Int = 4,
    ) = ZenSession(
        startedAt = startedAt,
        plannedDurationSeconds = plannedDurationSeconds,
        status = SessionStatus.ACTIVE,
        blockedAppCount = blockedAppCount,
    )

    @Test
    fun `starting a session persists it and assigns an id`() = runTest {
        val started = repository.startSession(activeSession())

        assertNotNull(started)
        assertTrue(started!!.id > 0)
        assertEquals(SessionStatus.ACTIVE, started.status)
        assertEquals(started, repository.getActiveSession())
        assertEquals(started, repository.observeActiveSession().first())
    }

    @Test
    fun `a second start is refused while a session is running`() = runTest {
        repository.startSession(activeSession(startedAt = 1_000L))

        val second = repository.startSession(activeSession(startedAt = 2_000L))

        assertNull(second)
        assertEquals(1_000L, repository.getActiveSession()?.startedAt)
    }

    @Test
    fun `starting a non-active session is a programming error`() = runTest {
        val error = runCatching {
            repository.startSession(activeSession().copy(status = SessionStatus.COMPLETED))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `completing the active session stores the outcome and clears the active slot`() = runTest {
        repository.startSession(activeSession(startedAt = 1_000L, plannedDurationSeconds = 60L))

        val finished = repository.finishActiveSession(
            status = SessionStatus.COMPLETED,
            endedAt = 61_000L,
            actualDurationSeconds = 60L,
        )

        assertNotNull(finished)
        assertEquals(SessionStatus.COMPLETED, finished!!.status)
        assertEquals(61_000L, finished.endedAt)
        assertEquals(60L, finished.actualDurationSeconds)
        assertNull(repository.getActiveSession())
    }

    @Test
    fun `cancelling stores the shorter actual duration`() = runTest {
        repository.startSession(activeSession(startedAt = 0L, plannedDurationSeconds = 3_600L))

        val cancelled = repository.finishActiveSession(
            status = SessionStatus.CANCELLED,
            endedAt = 600_000L,
            actualDurationSeconds = 600L,
        )

        assertEquals(SessionStatus.CANCELLED, cancelled?.status)
        assertEquals(600L, cancelled?.actualDurationSeconds)
        assertEquals(3_600L, cancelled?.plannedDurationSeconds)
    }

    @Test
    fun `finishing with nothing running returns null`() = runTest {
        val result = repository.finishActiveSession(
            status = SessionStatus.COMPLETED,
            endedAt = 1L,
            actualDurationSeconds = 0L,
        )

        assertNull(result)
    }

    @Test
    fun `a session can only be finished into a terminal state`() = runTest {
        repository.startSession(activeSession())

        val error = runCatching {
            repository.finishActiveSession(SessionStatus.PAUSED, endedAt = 1L, actualDurationSeconds = 0L)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `the blocked app count of the running session can be refreshed`() = runTest {
        repository.startSession(activeSession(blockedAppCount = 2))

        repository.updateActiveBlockedAppCount(9)

        assertEquals(9, repository.getActiveSession()?.blockedAppCount)
    }

    @Test
    fun `refreshing the blocked app count with nothing running does nothing`() = runTest {
        repository.updateActiveBlockedAppCount(9)

        assertNull(repository.getActiveSession())
    }
}
