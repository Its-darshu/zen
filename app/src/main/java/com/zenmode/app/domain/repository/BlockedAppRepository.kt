package com.zenmode.app.domain.repository

import com.zenmode.app.domain.model.BlockedApp
import kotlinx.coroutines.flow.Flow

/** The user's blocklist. Which apps exist on the device is a separate concern. */
interface BlockedAppRepository {

    /** Every app the user has toggled at least once, by display name. */
    fun observeBlockedApps(): Flow<List<BlockedApp>>

    /** Just the package names that are currently switched on: the blocking check. */
    fun observeEnabledPackages(): Flow<Set<String>>

    suspend fun getEnabledPackages(): Set<String>

    suspend fun countEnabled(): Int

    /** Adds or updates one entry. */
    suspend fun setBlocked(packageName: String, appName: String, enabled: Boolean)

    /** Adds or updates many entries at once. */
    suspend fun setBlockedApps(apps: List<BlockedApp>)

    /** Switches every known app off without forgetting the apps themselves. */
    suspend fun clearSelection()

    /**
     * Drops entries for apps that are no longer installed (specification §35).
     * A newly installed app is never added here: only the user adds apps.
     */
    suspend fun removeUninstalled(installedPackages: Set<String>)
}
