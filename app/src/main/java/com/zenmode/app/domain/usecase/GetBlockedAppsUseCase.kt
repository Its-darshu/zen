package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads the user's blocklist (specification §15). */
class GetBlockedAppsUseCase @Inject constructor(
    private val blockedAppRepository: BlockedAppRepository,
) {

    /** Every app the user has toggled, for the blocked-apps screen. */
    operator fun invoke(): Flow<List<BlockedApp>> = blockedAppRepository.observeBlockedApps()

    /** Just the packages to intercept: what the blocker checks against. */
    fun enabledPackages(): Flow<Set<String>> = blockedAppRepository.observeEnabledPackages()

    suspend fun enabledPackagesNow(): Set<String> = blockedAppRepository.getEnabledPackages()

    suspend fun enabledCount(): Int = blockedAppRepository.countEnabled()
}
