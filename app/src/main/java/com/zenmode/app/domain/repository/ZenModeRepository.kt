package com.zenmode.app.domain.repository

import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the *running* Zen session.
 *
 * The database is the single source of truth: an active session is the one row
 * whose status is [SessionStatus.ACTIVE]. Nothing about the countdown is cached
 * anywhere else, so a restarted process or service reads back the same session.
 *
 * The repository stores what it is told; deciding *which* transition to make is
 * the domain layer's job.
 */
interface ZenModeRepository {

    /** Emits the active session, or `null` when none is running. */
    fun observeActiveSession(): Flow<ZenSession?>

    suspend fun getActiveSession(): ZenSession?

    /**
     * Persists [session] as the active one.
     *
     * @return the stored session including its assigned id, or `null` if a
     *   session was already active — at most one session may be active at a time.
     */
    suspend fun startSession(session: ZenSession): ZenSession?

    /**
     * Closes the active session with a terminal [status].
     *
     * @return the stored session, or `null` if no session was active.
     */
    suspend fun finishActiveSession(
        status: SessionStatus,
        endedAt: Long,
        actualDurationSeconds: Long,
    ): ZenSession?

    /** Updates the blocked-app count of the active session, if any. */
    suspend fun updateActiveBlockedAppCount(count: Int)
}
