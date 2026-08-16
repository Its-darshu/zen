package com.zenmode.app.data.repository

import com.zenmode.app.data.local.dao.BlockedAppDao
import com.zenmode.app.data.local.entity.BlockedAppEntity
import com.zenmode.app.data.mapper.toDomain
import com.zenmode.app.data.mapper.toEntity
import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockedAppRepositoryImpl @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
) : BlockedAppRepository {

    override fun observeBlockedApps(): Flow<List<BlockedApp>> =
        blockedAppDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEnabledPackages(): Flow<Set<String>> =
        blockedAppDao.observeEnabledPackages().map { it.toSet() }

    override suspend fun getEnabledPackages(): Set<String> =
        blockedAppDao.getEnabledPackages().toSet()

    override suspend fun countEnabled(): Int = blockedAppDao.countEnabled()

    override suspend fun setBlocked(packageName: String, appName: String, enabled: Boolean) {
        blockedAppDao.upsert(
            BlockedAppEntity(packageName = packageName, appName = appName, enabled = enabled),
        )
    }

    override suspend fun setBlockedApps(apps: List<BlockedApp>) {
        if (apps.isEmpty()) return
        blockedAppDao.upsertAll(apps.map { it.toEntity() })
    }

    override suspend fun clearSelection() = blockedAppDao.disableAll()

    override suspend fun removeUninstalled(installedPackages: Set<String>) {
        // An empty set almost certainly means the package query failed rather than
        // that the device has no apps; wiping the user's blocklist on that basis
        // would be worse than doing nothing.
        if (installedPackages.isEmpty()) return
        blockedAppDao.deleteNotIn(installedPackages.toList())
    }
}
