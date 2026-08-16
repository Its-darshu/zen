package com.zenmode.app.domain.repository

import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.ZenSession
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the session record itself: history, statistics input and
 * housekeeping. The lifecycle of the *running* session lives in
 * [ZenModeRepository].
 */
interface SessionRepository {

    /** All sessions matching [filter], newest first. */
    fun observeSessions(filter: SessionFilter = SessionFilter.ALL): Flow<List<ZenSession>>

    /** Every completed session, newest first. The input to statistics and streaks. */
    fun observeCompletedSessions(): Flow<List<ZenSession>>

    /** Sessions that started within `[from, to)`, newest first. */
    fun observeSessionsStartedBetween(from: Long, to: Long): Flow<List<ZenSession>>

    suspend fun getSession(id: Long): ZenSession?

    suspend fun deleteSession(id: Long)

    /** Removes every stored session. Destructive: confirm before calling. */
    suspend fun clearHistory()
}
