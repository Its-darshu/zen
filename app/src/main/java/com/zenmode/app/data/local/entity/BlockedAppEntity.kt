package com.zenmode.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** An app on the user's blocklist (specification §21). */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,

    @ColumnInfo(name = "appName")
    val appName: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
)
