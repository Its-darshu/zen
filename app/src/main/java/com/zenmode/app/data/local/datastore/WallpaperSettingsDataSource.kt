package com.zenmode.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zenmode.app.domain.model.WallpaperSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallpaper choices in DataStore: two flags and two URI strings, nothing more.
 *
 * Image data is never stored here. The URI is a reference the system resolves
 * when the image is actually drawn, which is why a deleted photo shows up as a
 * load failure rather than as stale bytes.
 */
@Singleton
class WallpaperSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<WallpaperSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            WallpaperSettings(
                homeEnabled = preferences[Keys.HOME_ENABLED] ?: false,
                homeUri = preferences[Keys.HOME_URI]?.takeIf { it.isNotBlank() },
                lockEnabled = preferences[Keys.LOCK_ENABLED] ?: false,
                lockUri = preferences[Keys.LOCK_URI]?.takeIf { it.isNotBlank() },
            )
        }

    suspend fun getSettings(): WallpaperSettings = settings.first()

    suspend fun setHome(uri: String?, enabled: Boolean) = edit { preferences ->
        preferences[Keys.HOME_ENABLED] = enabled
        if (uri == null) preferences.remove(Keys.HOME_URI) else preferences[Keys.HOME_URI] = uri
    }

    suspend fun setLock(uri: String?, enabled: Boolean) = edit { preferences ->
        preferences[Keys.LOCK_ENABLED] = enabled
        if (uri == null) preferences.remove(Keys.LOCK_URI) else preferences[Keys.LOCK_URI] = uri
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // A wallpaper preference is never worth crashing the launcher over.
        }
    }

    private object Keys {
        val HOME_ENABLED = booleanPreferencesKey("wallpaper_home_enabled")
        val HOME_URI = stringPreferencesKey("wallpaper_home_uri")
        val LOCK_ENABLED = booleanPreferencesKey("wallpaper_lock_enabled")
        val LOCK_URI = stringPreferencesKey("wallpaper_lock_uri")
    }
}
