package com.zenmode.app.data.repository

import com.zenmode.app.data.local.datastore.RecentAppsDataSource
import com.zenmode.app.domain.repository.RecentAppsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentAppsRepositoryImpl @Inject constructor(
    private val dataSource: RecentAppsDataSource,
) : RecentAppsRepository {

    override fun observeRecentPackages(): Flow<List<String>> = dataSource.recentPackages

    override suspend fun getRecentPackages(): List<String> = dataSource.getRecentPackages()

    override suspend fun recordOpened(packageName: String) {
        dataSource.update { current ->
            RecentAppsDataSource.withMostRecent(current, packageName)
        }
    }

    override suspend fun remove(packageName: String) {
        dataSource.update { current -> current.filterNot { it == packageName } }
    }

    override suspend fun clear() {
        dataSource.update { emptyList() }
    }

    override suspend fun removeUninstalled(installedPackages: Set<String>) {
        // An empty set almost certainly means the package query failed rather
        // than that nothing is installed; wiping the history on that basis
        // would be worse than doing nothing.
        if (installedPackages.isEmpty()) return
        dataSource.update { current -> current.filter { it in installedPackages } }
    }
}
