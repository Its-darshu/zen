package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.SessionRepository
import javax.inject.Inject

/** Reads one stored session, e.g. the one the completion screen is about. */
class GetSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(id: Long): ZenSession? = sessionRepository.getSession(id)
}
