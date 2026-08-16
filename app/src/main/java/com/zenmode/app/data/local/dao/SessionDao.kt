package com.zenmode.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zenmode.app.data.local.entity.SessionEntity
import com.zenmode.app.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data access for sessions. Queries only: how a session moves between states,
 * and what the numbers mean, belongs to the domain layer.
 */
@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC")
    fun observeByStatus(status: SessionStatus): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE startedAt >= :from AND startedAt < :to " +
            "ORDER BY startedAt DESC",
    )
    fun observeStartedBetween(from: Long, to: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestByStatus(status: SessionStatus): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(status: SessionStatus): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    /**
     * Inserts [session] only while no other session is active, in one
     * transaction. This guards the "at most one active session" invariant
     * against two callers racing to start one.
     *
     * @return the new row id, or `null` if a session was already active.
     */
    @Transaction
    suspend fun insertIfNoneActive(session: SessionEntity): Long? {
        if (getLatestByStatus(SessionStatus.ACTIVE) != null) return null
        return insert(session)
    }

    /**
     * Applies [transform] to the active session, if there is one, inside a
     * transaction so a concurrent finish cannot be lost.
     *
     * @return the written row, or `null` if no session was active.
     */
    @Transaction
    suspend fun updateActive(transform: (SessionEntity) -> SessionEntity): SessionEntity? {
        val active = getLatestByStatus(SessionStatus.ACTIVE) ?: return null
        val updated = transform(active)
        update(updated)
        return updated
    }
}
