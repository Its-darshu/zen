package com.zenmode.app.domain.repository

import com.zenmode.app.domain.model.InstalledApp

/**
 * What is installed on the device.
 *
 * Separate from [BlockedAppRepository] because it is a different kind of data:
 * the blocklist is the user's, this is the platform's, and only the former is
 * stored.
 */
interface InstalledAppsRepository {

    /**
     * Apps the user may reasonably choose to block: everything with a launcher
     * entry, minus Zen Mode itself and the protected packages.
     */
    suspend fun getSelectableApps(): List<InstalledApp>

    /**
     * Packages Zen Mode will not block whatever the user selects — the dialer,
     * the launcher, Android's own settings and Zen Mode itself.
     *
     * Blocking the dialer would cut off calls; blocking Settings would take away
     * the user's means of switching the service off; blocking the launcher would
     * make the device unusable. All three are refused by design
     * (specification §4, §11, §36).
     */
    suspend fun getProtectedPackages(): Set<String>

    /**
     * Packages that stay reachable even in strict mode: Zen Mode itself, the
     * dialer, Android's settings, the system UI and the keyboard.
     *
     * Unlike [getProtectedPackages] this does **not** include the launcher —
     * strict mode blocks Home on purpose. Settings remains reachable, which is
     * what keeps a strict session recoverable.
     */
    suspend fun getEssentialPackages(): Set<String>

    /**
     * Everything the user can open, for the launcher's app drawer.
     *
     * Unlike [getSelectableApps] this keeps the dialer, Android's settings and
     * the rest of the protected set: a launcher that hides the phone app is not
     * a launcher. The two lists answer different questions — "what may I block"
     * versus "what may I open" — and only the first has anything excluded.
     */
    suspend fun getLaunchableApps(): List<InstalledApp>
}
