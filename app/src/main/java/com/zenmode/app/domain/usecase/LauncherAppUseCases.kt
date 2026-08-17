package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.model.LauncherApp
import com.zenmode.app.domain.repository.FavoriteAppsRepository
import com.zenmode.app.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The launcher's app list: everything openable, merged with what is pinned.
 *
 * The package manager is scanned **once per collection**, not per keystroke and
 * not per recomposition. Toggling a favourite re-emits from the stored set
 * without touching the package manager again, and searching is a pure filter
 * over the list already in memory.
 */
class GetLauncherAppsUseCase @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val favoriteAppsRepository: FavoriteAppsRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<LauncherApp>> =
        flow { emit(installedAppsRepository.getLaunchableApps()) }
            .flatMapLatest { installed ->
                favoriteAppsRepository.observeFavorites().map { favorites ->
                    installed.map { app ->
                        LauncherApp(
                            packageName = app.packageName,
                            appName = app.appName,
                            isFavorite = app.packageName in favorites,
                        )
                    }
                }
            }

    /** Just the pinned apps, in the same alphabetical order as the drawer. */
    fun favorites(): Flow<List<LauncherApp>> = invoke().map { apps -> apps.filter { it.isFavorite } }

    companion object {
        /**
         * Filters by app name, case-insensitively, ignoring surrounding
         * whitespace. An empty query matches everything.
         *
         * Matching is on the name only: a launcher search is for the label a
         * person reads, not for reverse-DNS package names.
         */
        fun search(apps: List<LauncherApp>, query: String): List<LauncherApp> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return apps
            return apps.filter { it.appName.contains(trimmed, ignoreCase = true) }
        }
    }
}

/**
 * Pins and unpins apps (launcher spec §6).
 *
 * Favourites for apps that are no longer installed are dropped whenever the
 * drawer has a fresh list to compare against.
 */
class UpdateFavoriteAppsUseCase @Inject constructor(
    private val favoriteAppsRepository: FavoriteAppsRepository,
) {

    suspend fun setFavorite(packageName: String, favorite: Boolean) =
        favoriteAppsRepository.setFavorite(packageName, favorite)

    suspend fun toggle(packageName: String) {
        val favorites = favoriteAppsRepository.getFavorites()
        favoriteAppsRepository.setFavorite(packageName, packageName !in favorites)
    }

    suspend fun removeUninstalled(installedPackages: Set<String>) =
        favoriteAppsRepository.removeUninstalled(installedPackages)
}
