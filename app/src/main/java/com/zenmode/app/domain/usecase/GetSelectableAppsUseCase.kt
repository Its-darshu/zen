package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.SelectableApp
import com.zenmode.app.domain.repository.BlockedAppRepository
import com.zenmode.app.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

/**
 * The blocked-apps screen's list: what is installed, merged with what the user
 * has switched on (specification §15).
 *
 * The installed list is read once per collection and combined with the stored
 * selection, so toggling an app updates immediately without re-querying the
 * package manager.
 */
class GetSelectableAppsUseCase @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val blockedAppRepository: BlockedAppRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<SelectableApp>> =
        flow { emit(installedAppsRepository.getSelectableApps()) }
            .flatMapLatest { installed ->
                blockedAppRepository.observeEnabledPackages().map { enabled ->
                    installed.map { app ->
                        SelectableApp(
                            packageName = app.packageName,
                            appName = app.appName,
                            isBlocked = app.packageName in enabled,
                            isSystemApp = app.isSystemApp,
                        )
                    }
                }
            }

    companion object {
        /**
         * Filters the list by name or package. Matching is case-insensitive and
         * ignores surrounding whitespace; an empty query matches everything.
         */
        fun search(apps: List<SelectableApp>, query: String): List<SelectableApp> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return apps
            return apps.filter { app ->
                app.appName.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true)
            }
        }
    }
}
