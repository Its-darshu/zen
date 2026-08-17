package com.zenmode.app.system.launcher

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Android will and will not tell a third-party launcher about tasks.
 *
 * Each flag is a fact about the platform, not a preference. They exist so the
 * UI can describe the truth instead of implying capabilities the app does not
 * have.
 */
data class TaskCapabilities(
    /** Can this app enumerate *other* apps' recent tasks? */
    val canReadOtherAppTasks: Boolean,
    /** Can it obtain a live preview image of another app? */
    val canReadTaskPreviews: Boolean,
    /** Can it remove another app's task from Android's Recents? */
    val canRemoveOtherAppTasks: Boolean,
    /** Can it resume another app's existing task directly, by task id? */
    val canResumeOtherAppTasks: Boolean,
)

/**
 * The Android adapter for task information — and, mostly, a record of what is
 * unavailable.
 *
 * The investigation, against `android.jar` for the compile SDK:
 *
 * - `ActivityManager.getRecentTasks(int, int)` — deprecated, and since API 21 it
 *   returns only the caller's own tasks. Useless for a Recents UI.
 * - `ActivityManager.getRunningTasks(int)` — same restriction, same uselessness.
 * - `ActivityManager.getAppTasks()` — own tasks only, by definition. Usable, but
 *   it only ever describes Zen Launcher itself.
 * - `TaskInfo` exposes `taskId`, `baseIntent`, `topActivity`, `isRunning` — and
 *   **no thumbnail of any kind**. There is no public snapshot or thumbnail class
 *   in the SDK at all.
 * - `moveTaskToFront(taskId, …)` requires REORDER_TASKS and a task id we cannot
 *   legitimately obtain for another app.
 * - `UsageStatsManager` could approximate a recent-apps list, but it needs the
 *   special usage-access permission, and the specification explicitly rules out
 *   taking that permission to fake Recents.
 *
 * So a third-party launcher **cannot** build Android's Recents. What it can do
 * honestly is remember the apps it opened itself, which is what
 * [com.zenmode.app.domain.repository.RecentAppsRepository] holds, and reopen
 * them with an ordinary launch intent — which Android resolves to the existing
 * task when one is running.
 *
 * Nothing here uses reflection, hidden APIs, shell commands, screen capture or
 * the accessibility service.
 */
@Singleton
class LauncherTaskProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val activityManager: ActivityManager?
        get() = context.getSystemService(ActivityManager::class.java)

    /**
     * Fixed for every supported version, because the restrictions are platform
     * policy rather than device configuration. Stated as data so the UI and the
     * tests can both rely on it.
     */
    fun capabilities(): TaskCapabilities = TaskCapabilities(
        canReadOtherAppTasks = false,
        canReadTaskPreviews = false,
        canRemoveOtherAppTasks = false,
        canResumeOtherAppTasks = false,
    )

    /**
     * This app's own tasks — the only ones Android exposes.
     *
     * Used to confirm the restriction rather than to populate the UI: it can
     * never contain anything but Zen Launcher and Zen Mode.
     */
    fun ownTaskPackages(): List<String> = runCatching {
        activityManager?.appTasks
            ?.mapNotNull { task -> task.taskInfo?.baseIntent?.component?.packageName }
            ?.distinct()
            .orEmpty()
    }.getOrElse {
        Log.w(TAG, "Could not read this app's own tasks", it)
        emptyList()
    }

    private companion object {
        const val TAG = "ZenTaskProvider"
    }
}
