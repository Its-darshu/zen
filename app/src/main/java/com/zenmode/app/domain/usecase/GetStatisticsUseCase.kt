package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.logic.StatisticsCalculator
import com.zenmode.app.domain.model.ZenStatistics
import com.zenmode.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Focus statistics (specification §17), recomputed whenever the session
 * history changes.
 */
class GetStatisticsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val calculator: StatisticsCalculator,
) {

    operator fun invoke(): Flow<ZenStatistics> =
        sessionRepository.observeCompletedSessions().map { calculator.calculate(it) }
}
