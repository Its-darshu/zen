package com.zenmode.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zenmode.app.domain.model.SessionStatus

/**
 * A stored Zen session (specification §20).
 *
 * `startedAt` and `endedAt` are epoch milliseconds; durations are seconds. The
 * indices back the two access patterns that matter: history ordered by start
 * time, and finding the single active session.
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["status"]),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "startedAt")
    val startedAt: Long,

    @ColumnInfo(name = "endedAt")
    val endedAt: Long?,

    @ColumnInfo(name = "plannedDurationSeconds")
    val plannedDurationSeconds: Long,

    @ColumnInfo(name = "actualDurationSeconds")
    val actualDurationSeconds: Long,

    @ColumnInfo(name = "status")
    val status: SessionStatus,

    @ColumnInfo(name = "blockedAppCount")
    val blockedAppCount: Int,
)
