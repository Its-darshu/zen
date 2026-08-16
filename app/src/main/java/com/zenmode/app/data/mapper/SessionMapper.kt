package com.zenmode.app.data.mapper

import com.zenmode.app.data.local.entity.BlockedAppEntity
import com.zenmode.app.data.local.entity.SessionEntity
import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.ZenSession

fun SessionEntity.toDomain(): ZenSession = ZenSession(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    plannedDurationSeconds = plannedDurationSeconds,
    actualDurationSeconds = actualDurationSeconds,
    status = status,
    blockedAppCount = blockedAppCount,
)

fun ZenSession.toEntity(): SessionEntity = SessionEntity(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    plannedDurationSeconds = plannedDurationSeconds,
    actualDurationSeconds = actualDurationSeconds,
    status = status,
    blockedAppCount = blockedAppCount,
)

fun BlockedAppEntity.toDomain(): BlockedApp = BlockedApp(
    packageName = packageName,
    appName = appName,
    enabled = enabled,
)

fun BlockedApp.toEntity(): BlockedAppEntity = BlockedAppEntity(
    packageName = packageName,
    appName = appName,
    enabled = enabled,
)
