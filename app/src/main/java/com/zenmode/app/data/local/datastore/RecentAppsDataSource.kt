package com.zenmode.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher's recent-app list in DataStore.
 *
 * Order carries the meaning here — position *is* recency — so this is stored as
 * one newline-delimited string rather than a `Set`, which would lose it. A
 * package name can contain neither a newline nor whitespace, so the encoding is
 * unambiguous, and both halves of it are pure functions with tests.
 *
 * Room was the alternative. It would have meant a schema migration on the
 * existing sessions database for what is a list of at most a dozen strings, so
 * DataStore is the cheaper correct answer.
 */
@Singleton
class RecentAppsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val recentPackages: Flow<List<String>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> decode(preferences[KEY_RECENTS]) }

    suspend fun getRecentPackages(): List<String> = recentPackages.first()

    suspend fun update(transform: (List<String>) -> List<String>) {
        try {
            dataStore.edit { preferences ->
                val updated = transform(decode(preferences[KEY_RECENTS]))
                preferences[KEY_RECENTS] = encode(updated)
            }
        } catch (_: IOException) {
            // Losing a history entry is never worth crashing the launcher.
        }
    }

    companion object {
        private val KEY_RECENTS = stringPreferencesKey("recent_packages")

        private const val SEPARATOR = "\n"

        /**
         * How many entries are kept. A stacked card list stops being readable
         * long before this, and an unbounded history would just grow forever.
         */
        const val MAX_ENTRIES = 12

        /** Encodes the ordered list. Blanks are dropped and the cap applied. */
        fun encode(packages: List<String>): String = packages
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_ENTRIES)
            .joinToString(SEPARATOR)

        /** Decodes the ordered list, tolerating anything malformed. */
        fun decode(stored: String?): List<String> = stored
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.take(MAX_ENTRIES)
            .orEmpty()

        /** Moves [packageName] to the front, keeping the rest in order. */
        fun withMostRecent(current: List<String>, packageName: String): List<String> {
            val trimmed = packageName.trim()
            if (trimmed.isEmpty()) return current
            return (listOf(trimmed) + current.filterNot { it == trimmed }).take(MAX_ENTRIES)
        }
    }
}
