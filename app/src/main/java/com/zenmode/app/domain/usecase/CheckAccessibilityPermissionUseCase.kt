package com.zenmode.app.domain.usecase

import com.zenmode.app.domain.permission.AccessibilityPermissionMonitor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Whether app blocking can currently work (specification §28, §34).
 *
 * A `false` answer is a normal state, not an error: the rest of the app stays
 * usable — sessions still run, the Zen screen still appears, history and
 * statistics still work — only the blocking is inert, and the UI says so
 * plainly with a way to fix it.
 */
class CheckAccessibilityPermissionUseCase @Inject constructor(
    private val monitor: AccessibilityPermissionMonitor,
) {

    operator fun invoke(): Flow<Boolean> = monitor.isEnabled

    suspend fun isEnabledNow(): Boolean = monitor.isEnabledNow()
}
