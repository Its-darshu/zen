package com.zenmode.app.system.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which app Android currently opens when Home is pressed. */
enum class DefaultLauncherState {
    /** Zen Launcher is the home app. */
    ZEN_LAUNCHER,

    /** Some other launcher is. */
    OTHER_LAUNCHER,

    /**
     * No default has been chosen, so Android asks every time. This is the state
     * right after installing a second launcher.
     */
    NOT_CHOSEN,
}

/**
 * Reads — and asks about — the default home app.
 *
 * **Android does not let an app make itself the launcher.** There is no API for
 * it, deliberately: the home app is one of the most powerful roles on the
 * device, so the choice belongs to the user in a system dialog. All this class
 * can do is report the truth and open the right screen for the user to decide.
 *
 * `RoleManager.ROLE_HOME` is the modern route and is available on every version
 * this app supports (API 29+). Where an OEM does not honour it, the home-app
 * settings screen is opened instead.
 */
@Singleton
class DefaultLauncherChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val roleManager: RoleManager?
        get() = context.getSystemService(RoleManager::class.java)

    /** The package Android currently resolves Home to, or null if it cannot say. */
    fun currentDefaultPackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return null

        val packageName = resolved.activityInfo?.packageName ?: return null
        // With no default chosen, Android resolves Home to its own chooser
        // rather than to a launcher.
        return if (packageName == ANDROID_RESOLVER_PACKAGE) null else packageName
    }

    fun state(): DefaultLauncherState = when (currentDefaultPackage()) {
        null -> DefaultLauncherState.NOT_CHOSEN
        context.packageName -> DefaultLauncherState.ZEN_LAUNCHER
        else -> DefaultLauncherState.OTHER_LAUNCHER
    }

    fun isZenLauncherDefault(): Boolean = state() == DefaultLauncherState.ZEN_LAUNCHER

    /**
     * The system dialog that asks the user to make this app the home app.
     *
     * @return null when the platform will not offer it, in which case
     *   [homeSettingsIntent] is the fallback.
     */
    fun requestHomeRoleIntent(): Intent? {
        val manager = roleManager ?: return null
        val available = runCatching { manager.isRoleAvailable(RoleManager.ROLE_HOME) }
            .getOrDefault(false)
        if (!available) return null
        // Asking again while we already hold it would show an empty dialog.
        if (runCatching { manager.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)) {
            return null
        }
        return runCatching { manager.createRequestRoleIntent(RoleManager.ROLE_HOME) }.getOrNull()
    }

    /**
     * Android's own home-app screen — the way *out* as much as in, since it is
     * where the user switches back to their previous launcher.
     */
    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the home-app settings, falling back to the general settings screen. */
    fun openHomeSettings(): Boolean {
        val candidates = listOf(
            homeSettingsIntent(),
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        candidates.forEach { intent ->
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private companion object {
        /** Where Home resolves when the user has not picked a launcher yet. */
        const val ANDROID_RESOLVER_PACKAGE = "android"
    }
}
