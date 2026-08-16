package com.zenmode.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zenmode.app.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

/** Data access for the blocklist. */
@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT packageName FROM blocked_apps WHERE enabled = 1")
    fun observeEnabledPackages(): Flow<List<String>>

    @Query("SELECT packageName FROM blocked_apps WHERE enabled = 1")
    suspend fun getEnabledPackages(): List<String>

    @Query("SELECT COUNT(*) FROM blocked_apps WHERE enabled = 1")
    suspend fun countEnabled(): Int

    @Upsert
    suspend fun upsert(app: BlockedAppEntity)

    @Upsert
    suspend fun upsertAll(apps: List<BlockedAppEntity>)

    @Query("UPDATE blocked_apps SET enabled = 0")
    suspend fun disableAll()

    @Query("DELETE FROM blocked_apps WHERE packageName NOT IN (:installedPackages)")
    suspend fun deleteNotIn(installedPackages: List<String>)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()
}
