package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.logic.SessionHistoryGrouper
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionHistoryGroup
import com.zenmode.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Session history, grouped by day and filtered by outcome (specification §19). */
class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val grouper: SessionHistoryGrouper,
) {

    operator fun invoke(
        filter: SessionFilter = SessionFilter.ALL,
    ): Flow<List<SessionHistoryGroup>> =
        sessionRepository.observeSessions(filter).map { grouper.group(it) }
}
