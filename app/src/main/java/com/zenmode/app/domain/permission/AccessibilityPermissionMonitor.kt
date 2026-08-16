package com.zenmode.app.domain.permission

import kotlinx.coroutines.flow.Flow

/**
 * Whether the user has granted Zen Mode accessibility access.
 *
 * The domain only needs the answer, not the Android mechanism that produces it,
 * so this interface keeps `Settings.Secure` out of the business rules. The
 * platform implementation is bound in the Android integration layer.
 *
 * The permission can be revoked at any moment from Android's own settings —
 * that is the user's right, and every consumer treats it as normal.
 */
interface AccessibilityPermissionMonitor {

    /** Emits the current grant state, and again whenever it changes. */
    val isEnabled: Flow<Boolean>

    /** A one-shot read, for decisions that cannot wait for a flow. */
    suspend fun isEnabledNow(): Boolean
}
