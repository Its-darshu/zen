package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.repository.BlockedAppRepository
import com.zenmode.app.domain.repository.ZenModeRepository
import javax.inject.Inject

/**
 * Changes the blocklist (specification §15, §35).
 *
 * Editing the list while a session runs is allowed — the blocker reads the
 * current list on every check — so the running session's recorded blocked-app
 * count is kept in step here rather than left stale.
 *
 * No app is ever added on the app's own initiative. A newly installed app stays
 * unblocked until the user says otherwise; only uninstalled apps are removed.
 */
class UpdateBlockedAppsUseCase @Inject constructor(
    private val blockedAppRepository: BlockedAppRepository,
    private val zenModeRepository: ZenModeRepository,
) {

    suspend fun setBlocked(packageName: String, appName: String, enabled: Boolean) {
        blockedAppRepository.setBlocked(packageName, appName, enabled)
        syncActiveSessionCount()
    }

    suspend fun setSelection(apps: List<BlockedApp>) {
        blockedAppRepository.setBlockedApps(apps)
        syncActiveSessionCount()
    }

    /** Switches every app off without forgetting the apps themselves. */
    suspend fun clearSelection() {
        blockedAppRepository.clearSelection()
        syncActiveSessionCount()
    }

    /** Drops apps that are no longer installed. */
    suspend fun removeUninstalled(installedPackages: Set<String>) {
        blockedAppRepository.removeUninstalled(installedPackages)
        syncActiveSessionCount()
    }

    private suspend fun syncActiveSessionCount() {
        if (zenModeRepository.getActiveSession() == null) return
        zenModeRepository.updateActiveBlockedAppCount(blockedAppRepository.countEnabled())
    }
}
