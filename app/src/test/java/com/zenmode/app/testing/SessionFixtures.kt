package com.zenmode.app.testing

import com.zenmode.app.data.local.entity.SessionEntity
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession

/** Builds a session row, so tests only state the fields they care about. */
fun sessionEntity(
    id: Long = 0,
    startedAt: Long = 1_000L,
    endedAt: Long? = null,
    plannedDurationSeconds: Long = 1_500L,
    actualDurationSeconds: Long = 0L,
    status: SessionStatus = SessionStatus.COMPLETED,
    blockedAppCount: Int = 3,
): SessionEntity = SessionEntity(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    plannedDurationSeconds = plannedDurationSeconds,
    actualDurationSeconds = actualDurationSeconds,
    status = status,
    blockedAppCount = blockedAppCount,
)

/** Builds a domain session, so tests only state the fields they care about. */
fun zenSession(
    id: Long = 0,
    startedAt: Long = 0L,
    endedAt: Long? = null,
    plannedDurationSeconds: Long = 1_500L,
    actualDurationSeconds: Long = 0L,
    status: SessionStatus = SessionStatus.COMPLETED,
    blockedAppCount: Int = 3,
): ZenSession = ZenSession(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    plannedDurationSeconds = plannedDurationSeconds,
    actualDurationSeconds = actualDurationSeconds,
    status = status,
    blockedAppCount = blockedAppCount,
)

/**
 * A completed session that finished on [endedAt] having run for
 * [durationSeconds] — the shape streak and statistics tests care about.
 */
fun completedSessionEndingAt(
    endedAt: Long,
    durationSeconds: Long = 1_500L,
    id: Long = 0,
): ZenSession = ZenSession(
    id = id,
    startedAt = endedAt - durationSeconds * 1_000L,
    endedAt = endedAt,
    plannedDurationSeconds = durationSeconds,
    actualDurationSeconds = durationSeconds,
    status = SessionStatus.COMPLETED,
    blockedAppCount = 3,
)
