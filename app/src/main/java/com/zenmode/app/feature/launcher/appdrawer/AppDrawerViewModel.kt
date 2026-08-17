package com.zenmode.app.feature.launcher.appdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.LauncherApp
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetLauncherAppsUseCase
import com.zenmode.app.domain.usecase.RecordAppOpenedUseCase
import com.zenmode.app.domain.usecase.UpdateFavoriteAppsUseCase
import com.zenmode.app.system.launcher.AppLaunchResult
import com.zenmode.app.system.launcher.AppLauncher
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

data class AppDrawerUiState(
    val isLoading: Boolean = true,
    val apps: List<LauncherApp> = emptyList(),
    val query: String = "",
    val sessionActive: Boolean = false,
) {
    val isEmpty: Boolean get() = apps.isEmpty()
}

sealed interface AppDrawerEvent {
    data class Error(val message: String) : AppDrawerEvent

    /** A session started; the drawer must not stay open over it. */
    data object SessionStarted : AppDrawerEvent
}

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val appLauncher: AppLauncher,
    private val updateFavorites: UpdateFavoriteAppsUseCase,
    private val recordAppOpened: RecordAppOpenedUseCase,
    getLauncherApps: GetLauncherAppsUseCase,
    getActiveSession: GetActiveSessionUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val events = Channel<AppDrawerEvent>(Channel.BUFFERED)
    val eventFlow: Flow<AppDrawerEvent> = events.receiveAsFlow()

    /**
     * The list is produced once per collection and filtered in memory. Typing
     * never touches the package manager.
     */
    private val allApps: Flow<List<LauncherApp>> = getLauncherApps()
        // An app pinned before it was uninstalled is dropped here, where we
        // know what is actually installed.
        .onEach { apps -> updateFavorites.removeUninstalled(apps.map { it.packageName }.toSet()) }

    val uiState: StateFlow<AppDrawerUiState> = combine(
        allApps,
        query,
        getActiveSession(),
    ) { apps, currentQuery, session ->
        AppDrawerUiState(
            isLoading = false,
            apps = GetLauncherAppsUseCase.search(apps, currentQuery),
            query = currentQuery,
            sessionActive = session != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppDrawerUiState(),
    )

    fun setQuery(query: String) {
        this.query.value = query
    }

    fun launch(app: LauncherApp) {
        // Blocking stays where it already lives: the accessibility service sees
        // whatever comes to the front and redirects it. The launcher does not
        // get a second opinion about what may run.
        val event = when (appLauncher.launch(app.packageName)) {
            // Recorded only once the launch succeeded, so the recents list never
            // holds an app that would not open.
            AppLaunchResult.Launched -> {
                viewModelScope.launch { recordAppOpened(app.packageName) }
                null
            }
            AppLaunchResult.NoLaunchableActivity ->
                AppDrawerEvent.Error("${app.appName} has nothing to open.")
            AppLaunchResult.NotInstalled ->
                AppDrawerEvent.Error("${app.appName} is no longer installed.")
        }
        if (event != null) viewModelScope.launch { events.send(event) }
    }

    fun toggleFavorite(app: LauncherApp) {
        viewModelScope.launch { updateFavorites.setFavorite(app.packageName, !app.isFavorite) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
