package com.zenmode.app.data.repository

import com.zenmode.app.data.local.datastore.FavoritesDataSource
import com.zenmode.app.domain.repository.FavoriteAppsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteAppsRepositoryImpl @Inject constructor(
    private val favoritesDataSource: FavoritesDataSource,
) : FavoriteAppsRepository {

    override fun observeFavorites(): Flow<Set<String>> = favoritesDataSource.favorites

    override suspend fun getFavorites(): Set<String> = favoritesDataSource.getFavorites()

    override suspend fun addFavorite(packageName: String) {
        if (packageName.isBlank()) return
        favoritesDataSource.update { it + packageName }
    }

    override suspend fun removeFavorite(packageName: String) {
        favoritesDataSource.update { it - packageName }
    }

    override suspend fun setFavorite(packageName: String, favorite: Boolean) {
        if (favorite) addFavorite(packageName) else removeFavorite(packageName)
    }

    override suspend fun removeUninstalled(installedPackages: Set<String>) {
        if (installedPackages.isEmpty()) return
        favoritesDataSource.update { favorites -> favorites.filterTo(mutableSetOf()) { it in installedPackages } }
    }
}
