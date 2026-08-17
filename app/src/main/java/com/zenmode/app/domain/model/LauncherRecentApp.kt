package com.zenmode.app.domain.model

/**
 * An app the user recently opened **from Zen Launcher**.
 *
 * This is deliberately not "Android's recent tasks". Android does not let a
 * third-party launcher enumerate other apps' tasks — `getRecentTasks` and
 * `getRunningTasks` have returned only the caller's own tasks since API 21, and
 * `getAppTasks` is own-tasks-only by definition. The only honest source left is
 * what this launcher itself opened, so that is exactly what this models.
 *
 * No task objects, no `Context`, no screenshots: a package name, a label, and
 * the position in the list. The label is resolved fresh from the package
 * manager, so a renamed or uninstalled app never shows stale text.
 */
data class LauncherRecentApp(
    val packageName: String,
    val appName: String,
    /** 0 is the most recently opened. Drives the stacking order. */
    val position: Int,
)
