package com.zenmode.app.data.repository

import com.zenmode.app.data.local.dao.SessionDao
import com.zenmode.app.data.mapper.toDomain
import com.zenmode.app.data.mapper.toEntity
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.ZenModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZenModeRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : ZenModeRepository {

    override fun observeActiveSession(): Flow<ZenSession?> =
        sessionDao.observeLatestByStatus(SessionStatus.ACTIVE).map { it?.toDomain() }

    override suspend fun getActiveSession(): ZenSession? =
        sessionDao.getLatestByStatus(SessionStatus.ACTIVE)?.toDomain()

    override suspend fun startSession(session: ZenSession): ZenSession? {
        require(session.status == SessionStatus.ACTIVE) {
            "A session must be ACTIVE when it is started, was ${session.status}"
        }
        val id = sessionDao.insertIfNoneActive(session.toEntity()) ?: return null
        return session.copy(id = id)
    }

    override suspend fun finishActiveSession(
        status: SessionStatus,
        endedAt: Long,
        actualDurationSeconds: Long,
    ): ZenSession? {
        require(status.isTerminal) { "A session can only be finished into a terminal state" }
        return sessionDao.updateActive { active ->
            active.copy(
                status = status,
                endedAt = endedAt,
                actualDurationSeconds = actualDurationSeconds,
            )
        }?.toDomain()
    }

    override suspend fun updateActiveBlockedAppCount(count: Int) {
        sessionDao.updateActive { it.copy(blockedAppCount = count) }
    }
}
