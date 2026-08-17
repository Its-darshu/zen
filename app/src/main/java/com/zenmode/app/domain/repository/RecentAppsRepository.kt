package com.zenmode.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The launcher's own record of which apps it opened, most recent first.
 *
 * An ordered list of package names and nothing else. It is the launcher's
 * history, not Android's task stack — see [com.zenmode.app.domain.model.LauncherRecentApp].
 */
interface RecentAppsRepository {

    /** Package names, most recently opened first. */
    fun observeRecentPackages(): Flow<List<String>>

    suspend fun getRecentPackages(): List<String>

    /**
     * Records an app as just opened, moving it to the front.
     *
     * Opening something already in the list re-orders rather than duplicates it.
     */
    suspend fun recordOpened(packageName: String)

    /** Removes one entry from the launcher's list. Does not close the app. */
    suspend fun remove(packageName: String)

    suspend fun clear()

    /** Drops entries for apps that are no longer installed. */
    suspend fun removeUninstalled(installedPackages: Set<String>)
}
