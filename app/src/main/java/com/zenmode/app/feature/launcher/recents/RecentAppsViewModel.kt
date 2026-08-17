package com.zenmode.app.feature.launcher.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.LauncherRecentApp
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetRecentAppsUseCase
import com.zenmode.app.domain.usecase.RecordAppOpenedUseCase
import com.zenmode.app.domain.usecase.UpdateRecentAppsUseCase
import com.zenmode.app.system.launcher.AppLaunchResult
import com.zenmode.app.system.launcher.AppLauncher
import com.zenmode.app.system.launcher.LauncherTaskProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<LauncherRecentApp> = emptyList(),
    val sessionActive: Boolean = false,
    /**
     * False on every Android version: no public API lets one app close
     * another's task. Kept as state rather than hardcoded in the UI so the
     * absence is explicit and testable.
     */
    val canRemoveFromAndroidRecents: Boolean = false,
) {
    val isEmpty: Boolean get() = apps.isEmpty()
}

sealed interface RecentAppsEvent {
    data class Error(val message: String) : RecentAppsEvent
    data object SessionStarted : RecentAppsEvent
}

@HiltViewModel
class RecentAppsViewModel @Inject constructor(
    private val appLauncher: AppLauncher,
    private val recordAppOpened: RecordAppOpenedUseCase,
    private val updateRecentApps: UpdateRecentAppsUseCase,
    taskProvider: LauncherTaskProvider,
    getRecentApps: GetRecentAppsUseCase,
    getActiveSession: GetActiveSessionUseCase,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    private val events = Channel<RecentAppsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RecentAppsEvent> = events.receiveAsFlow()

    private val capabilities = taskProvider.capabilities()

    val uiState: StateFlow<RecentAppsUiState> = combine(
        getRecentApps().onEach { apps ->
            // Anything uninstalled since it was recorded is dropped here, where
            // the installed list has just been read.
            updateRecentApps.removeUninstalled(apps.map { it.packageName }.toSet())
        },
        getActiveSession(),
        refreshTrigger,
    ) { apps, session, _ ->
        RecentAppsUiState(
            isLoading = false,
            apps = apps,
            sessionActive = session != null,
            canRemoveFromAndroidRecents = capabilities.canRemoveOtherAppTasks,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecentAppsUiState(),
    )

    /**
     * Re-reads when the screen opens or the launcher resumes. There is no
     * polling: the list only changes when the launcher opens something.
     */
    fun refresh() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    /**
     * Reopens the app with an ordinary launch intent, which Android resolves to
     * the app's existing task when one is running — so this returns to where the
     * user left off without needing a task id the launcher cannot have.
     */
    fun open(app: LauncherRecentApp) {
        when (appLauncher.launch(app.packageName)) {
            AppLaunchResult.Launched -> viewModelScope.launch {
                recordAppOpened(app.packageName)
            }
            AppLaunchResult.NoLaunchableActivity -> sendError("${app.appName} has nothing to open.")
            AppLaunchResult.NotInstalled -> {
                sendError("${app.appName} is no longer installed.")
                viewModelScope.launch { updateRecentApps.remove(app.packageName) }
            }
        }
    }

    /**
     * Removes the card from *this list*. The app keeps running if it was
     * running: closing another app's task is not something Android permits.
     */
    fun removeFromList(app: LauncherRecentApp) {
        viewModelScope.launch { updateRecentApps.remove(app.packageName) }
    }

    fun clearAll() {
        viewModelScope.launch { updateRecentApps.clear() }
    }

    private fun sendError(message: String) {
        viewModelScope.launch { events.send(RecentAppsEvent.Error(message)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
