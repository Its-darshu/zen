package com.zenmode.app.data.local.packages

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.zenmode.app.domain.model.InstalledApp
import com.zenmode.app.di.IoDispatcher
import com.zenmode.app.domain.repository.InstalledAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the installed-app list from [PackageManager].
 *
 * Only apps with a launcher entry are offered: those are the ones a user can
 * open and therefore be distracted by. Package visibility is declared with a
 * `<queries>` element in the manifest rather than `QUERY_ALL_PACKAGES`, which
 * would ask for far more access than this needs.
 *
 * Nothing about the device's apps leaves the phone — the list is read on demand
 * and never stored beyond the user's own selection.
 */
@Singleton
class AndroidInstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InstalledAppsRepository {

    private val packageManager: PackageManager get() = context.packageManager

    override suspend fun getSelectableApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val protected = protectedPackages()
        // The blocklist must not offer the apps that are never blocked anyway.
        queryLaunchableApps().filterNot { it.packageName in protected }
    }

    override suspend fun getLaunchableApps(): List<InstalledApp> = withContext(ioDispatcher) {
        // The drawer shows everything openable, protected apps included: a
        // launcher without the dialer or Settings would be useless.
        queryLaunchableApps()
    }

    /**
     * The one package-manager scan, shared by both lists.
     *
     * Membership is decided by having a launcher activity, which is Android's
     * own definition of "the user can open this" — no hardcoded package names,
     * and system components without an entry point never appear.
     */
    private fun queryLaunchableApps(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo -> resolveInfo.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = runCatching { packageManager.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    isSystemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    override suspend fun getProtectedPackages(): Set<String> = withContext(ioDispatcher) {
        protectedPackages()
    }

    override suspend fun getEssentialPackages(): Set<String> = withContext(ioDispatcher) {
        // Everything protected except the launcher: strict mode blocks Home.
        //
        // Zen Mode's own package is added back afterwards. Once Zen Launcher is
        // the default launcher, the resolved home package *is* this app — and
        // subtracting it would make strict mode redirect the app to itself,
        // forever.
        (protectedPackages() - launcherPackages()) + context.packageName
    }

    private fun launcherPackages(): Set<String> = buildSet {
        resolvePackage(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(::add)
    }

    /**
     * Resolved from the system rather than hardcoded, so this works on any
     * device and any launcher (specification §41.15).
     */
    private fun protectedPackages(): Set<String> = buildSet {
        add(context.packageName)
        resolvePackage(Intent(Intent.ACTION_DIAL))?.let(::add)
        resolvePackage(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(::add)
        resolvePackage(Intent(Settings.ACTION_SETTINGS))?.let(::add)
        resolvePackage(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))?.let(::add)

        // The keyboard, so a session cannot leave the user unable to type.
        currentInputMethodPackage()?.let(::add)

        // The system UI hosts the status bar, the notification shade, the
        // recents view and the lock screen, including the emergency-call entry
        // point. Interrupting it would be both useless and dangerous, and there
        // is no intent to resolve it by.
        addAll(ALWAYS_EXEMPT_SYSTEM_PACKAGES)
    }

    /** The keyboard currently in use, read from the system's own setting. */
    private fun currentInputMethodPackage(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun resolvePackage(intent: Intent): String? = runCatching {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }.getOrNull()

    private companion object {
        /**
         * Platform UI that has no resolvable intent. These are Android's own
         * packages, not third-party apps the user might want blocked, so naming
         * them here does not conflict with the rule against hardcoding app
         * packages the user is meant to configure.
         */
        val ALWAYS_EXEMPT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.emergency",
            "com.android.server.telecom",
        )
    }
}
