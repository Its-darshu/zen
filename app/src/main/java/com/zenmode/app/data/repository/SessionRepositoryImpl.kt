package com.zenmode.app.data.repository

import com.zenmode.app.data.local.dao.SessionDao
import com.zenmode.app.data.mapper.toDomain
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {

    override fun observeSessions(filter: SessionFilter): Flow<List<ZenSession>> {
        val entities = when (filter) {
            SessionFilter.ALL -> sessionDao.observeAll()
            SessionFilter.COMPLETED -> sessionDao.observeByStatus(SessionStatus.COMPLETED)
            SessionFilter.CANCELLED -> sessionDao.observeByStatus(SessionStatus.CANCELLED)
        }
        return entities.map { list -> list.map { it.toDomain() } }
    }

    override fun observeCompletedSessions(): Flow<List<ZenSession>> =
        sessionDao.observeByStatus(SessionStatus.COMPLETED)
            .map { list -> list.map { it.toDomain() } }

    override fun observeSessionsStartedBetween(from: Long, to: Long): Flow<List<ZenSession>> =
        sessionDao.observeStartedBetween(from, to)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getSession(id: Long): ZenSession? = sessionDao.getById(id)?.toDomain()

    override suspend fun deleteSession(id: Long) = sessionDao.deleteById(id)

    override suspend fun clearHistory() = sessionDao.deleteAll()
}
