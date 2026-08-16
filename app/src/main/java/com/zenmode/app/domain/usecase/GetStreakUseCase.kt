package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.logic.StreakCalculator
import com.zenmode.app.domain.model.ZenStreak
import com.zenmode.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Current and best streak (specification §18), derived from completed sessions.
 */
class GetStreakUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val calculator: StreakCalculator,
) {

    operator fun invoke(): Flow<ZenStreak> =
        sessionRepository.observeCompletedSessions().map { calculator.calculate(it) }
}
