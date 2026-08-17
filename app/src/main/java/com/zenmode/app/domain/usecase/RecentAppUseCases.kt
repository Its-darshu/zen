package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.LauncherRecentApp
import com.zenmode.app.domain.repository.InstalledAppsRepository
import com.zenmode.app.domain.repository.RecentAppsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The launcher's recent-app list, resolved against what is actually installed.
 *
 * Labels come from the package manager rather than from storage, so a renamed
 * app shows its new name and an uninstalled one disappears instead of leaving a
 * card that cannot open. The package list is read once per collection, not per
 * emission.
 */
class GetRecentAppsUseCase @Inject constructor(
    private val recentAppsRepository: RecentAppsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<LauncherRecentApp>> =
        flow { emit(installedAppsRepository.getLaunchableApps()) }
            .flatMapLatest { installed ->
                val namesByPackage = installed.associate { it.packageName to it.appName }
                recentAppsRepository.observeRecentPackages().map { packages ->
                    packages
                        .filter { it in namesByPackage }
                        .mapIndexed { index, packageName ->
                            LauncherRecentApp(
                                packageName = packageName,
                                appName = namesByPackage.getValue(packageName),
                                position = index,
                            )
                        }
                }
            }
}

/**
 * Records that an app was opened from the launcher.
 *
 * Called after a launch actually succeeds, so a failed launch never leaves a
 * card for an app that would not open.
 */
class RecordAppOpenedUseCase @Inject constructor(
    private val recentAppsRepository: RecentAppsRepository,
) {
    suspend operator fun invoke(packageName: String) =
        recentAppsRepository.recordOpened(packageName)
}

/**
 * Edits the launcher's recent list.
 *
 * [remove] takes an app off *this list*. It does not, and cannot, close the app
 * or touch Android's own Recents — no public API allows that for another app.
 */
class UpdateRecentAppsUseCase @Inject constructor(
    private val recentAppsRepository: RecentAppsRepository,
) {
    suspend fun remove(packageName: String) = recentAppsRepository.remove(packageName)

    suspend fun clear() = recentAppsRepository.clear()

    suspend fun removeUninstalled(installedPackages: Set<String>) =
        recentAppsRepository.removeUninstalled(installedPackages)
}
