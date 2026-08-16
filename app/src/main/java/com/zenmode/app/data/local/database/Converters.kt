package com.zenmode.app.data.local.database

import androidx.room.TypeConverter
import com.zenmode.app.domain.model.SessionStatus

/**
 * Room type converters.
 *
 * [SessionStatus] is stored by name rather than ordinal so reordering the enum
 * cannot silently reinterpret existing rows. An unrecognised name decays to
 * [SessionStatus.CANCELLED] instead of throwing: a row written by a future
 * version of the app must never crash history (specification §34).
 */
class Converters {

    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String = status.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus =
        SessionStatus.entries.firstOrNull { it.name == value } ?: SessionStatus.CANCELLED
}
