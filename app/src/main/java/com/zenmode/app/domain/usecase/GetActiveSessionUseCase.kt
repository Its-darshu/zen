package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.ZenModeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The running session, or `null`.
 *
 * Every consumer — the Zen screen, the foreground service, the blocker — reads
 * the same flow, so there is exactly one answer to "is Zen Mode on" in the app.
 */
class GetActiveSessionUseCase @Inject constructor(
    private val zenModeRepository: ZenModeRepository,
) {

    operator fun invoke(): Flow<ZenSession?> = zenModeRepository.observeActiveSession()

    /** One-shot read, for callers that cannot collect a flow. */
    suspend fun current(): ZenSession? = zenModeRepository.getActiveSession()
}
