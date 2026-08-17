package com.zenmode.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pinned apps, stored as a set of package names in DataStore.
 *
 * A set is exactly the right shape: pinning twice is a no-op for free, the data
 * is tiny, and nothing platform-owned is persisted. As everywhere else in this
 * app, an unreadable store falls back to empty rather than throwing.
 */
@Singleton
class FavoritesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val favorites: Flow<Set<String>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences[KEY_FAVORITES].orEmpty() }

    suspend fun getFavorites(): Set<String> = favorites.first()

    suspend fun update(transform: (Set<String>) -> Set<String>) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_FAVORITES] = transform(preferences[KEY_FAVORITES].orEmpty())
            }
        } catch (_: IOException) {
            // Losing a pin is not worth crashing the launcher over.
        }
    }

    private companion object {
        val KEY_FAVORITES = stringSetPreferencesKey("favorite_packages")
    }
}
