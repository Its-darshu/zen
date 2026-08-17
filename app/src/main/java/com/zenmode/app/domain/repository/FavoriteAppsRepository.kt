package com.zenmode.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The apps the user has pinned to the launcher home screen.
 *
 * A set of package names and nothing more, so it stays small, survives
 * restarts, and can never hold a stale reference to something platform-owned.
 */
interface FavoriteAppsRepository {

    fun observeFavorites(): Flow<Set<String>>

    suspend fun getFavorites(): Set<String>

    /** Pinning something already pinned changes nothing: the store is a set. */
    suspend fun addFavorite(packageName: String)

    suspend fun removeFavorite(packageName: String)

    suspend fun setFavorite(packageName: String, favorite: Boolean)

    /**
     * Drops pins for apps that are no longer installed.
     *
     * An empty [installedPackages] is ignored: it almost certainly means the
     * package query failed, and wiping the user's favourites on that basis
     * would be worse than doing nothing.
     */
    suspend fun removeUninstalled(installedPackages: Set<String>)
}
