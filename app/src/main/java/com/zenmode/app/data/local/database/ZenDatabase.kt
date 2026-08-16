package com.zenmode.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zenmode.app.data.local.dao.BlockedAppDao
import com.zenmode.app.data.local.dao.SessionDao
import com.zenmode.app.data.local.entity.BlockedAppEntity
import com.zenmode.app.data.local.entity.SessionEntity

/**
 * The on-device database. Nothing here is ever uploaded (specification §30).
 *
 * Schemas are exported to `app/schemas` so future migrations can be written and
 * tested against the real historical schema.
 */
@Database(
    entities = [SessionEntity::class, BlockedAppEntity::class],
    version = ZenDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ZenDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun blockedAppDao(): BlockedAppDao

    companion object {
        const val VERSION = 1
        const val NAME = "zen_mode.db"
    }
}
