package com.zenmode.app.feature.launcher.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.core.time.DurationFormat
import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.ZenTimer
import com.zenmode.app.domain.model.LauncherApp
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetLauncherAppsUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.domain.usecase.GetWallpaperSettingsUseCase
import com.zenmode.app.domain.usecase.RecordAppOpenedUseCase
import com.zenmode.app.domain.usecase.UpdateFavoriteAppsUseCase
import com.zenmode.app.domain.usecase.UpdateWallpaperUseCase
import com.zenmode.app.system.launcher.AppLaunchResult
import com.zenmode.app.system.launcher.AppLauncher
import com.zenmode.app.system.launcher.WallpaperController
import com.zenmode.app.system.launcher.WallpaperSlot
import com.zenmode.app.system.launcher.WallpaperUriPermissionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launcher home screen's state.
 *
 * [remainingText] is derived from the *existing* Zen session through the
 * existing [ZenTimer]; the launcher does not own a timer and does not keep any
 * session state of its own.
 */
data class LauncherHomeUiState(
    val isLoading: Boolean = true,
    val clockText: String = "",
    val dateText: String = "",
    val sessionActive: Boolean = false,
    val remainingText: String = "",
    val favorites: List<LauncherApp> = emptyList(),
    /**
     * The chosen home image, or null for a black background. Always null while
     * a session runs: the Zen presentation stays pure black.
     */
    val wallpaperUri: String? = null,
)

@HiltViewModel
class LauncherHomeViewModel @Inject constructor(
    private val zenTimer: ZenTimer,
    private val clock: ZenClock,
    private val appLauncher: AppLauncher,
    private val updateWallpaper: UpdateWallpaperUseCase,
    private val updateFavorites: UpdateFavoriteAppsUseCase,
    private val recordAppOpened: RecordAppOpenedUseCase,
    private val wallpaperController: WallpaperController,
    private val getWallpaperSettings: GetWallpaperSettingsUseCase,
    getActiveSession: GetActiveSessionUseCase,
    getSettings: GetSettingsUseCase,
    getLauncherApps: GetLauncherAppsUseCase,
) : ViewModel() {

    private val events = Channel<String>(Channel.BUFFERED)

    /** Messages worth showing the user, e.g. an app that has gone away. */
    val messages: Flow<String> = events.receiveAsFlow()

    /**
     * A redraw trigger, not a clock. Everything shown is re-derived from stored
     * timestamps on each tick, so a missed tick cannot make anything drift.
     */
    private val ticker: Flow<Long> = flow {
        while (true) {
            val now = clock.nowMillis()
            emit(now)
            delay(MILLIS_PER_SECOND - now % MILLIS_PER_SECOND)
        }
    }

    val uiState: StateFlow<LauncherHomeUiState> = combine(
        getActiveSession(),
        getSettings(),
        ticker,
        getLauncherApps.favorites(),
        getWallpaperSettings(),
    ) { session, settings, now, favorites, wallpaper ->
        val snapshot = zenTimer.snapshotAt(session, now)
        LauncherHomeUiState(
            isLoading = false,
            clockText = DurationFormat.clock(now, clock.zone(), settings.use24HourClock),
            dateText = DurationFormat.longDate(now, clock.zone()),
            sessionActive = session != null,
            remainingText = DurationFormat.timer(snapshot.remainingSeconds),
            favorites = favorites,
            // Zen Mode's own visual rules win: a running session is pure black,
            // whatever wallpaper the launcher has been given.
            wallpaperUri = wallpaper.homeUri.takeIf { session == null && wallpaper.hasHomeWallpaper },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LauncherHomeUiState(),
    )

    /**
     * The chosen image could not be decoded — deleted, or its permission
     * revoked. The choice is forgotten so the home screen stops retrying it,
     * and the user is told rather than left staring at an unexplained black
     * screen.
     */
    fun onWallpaperUnavailable() {
        viewModelScope.launch {
            val previous = getWallpaperSettings.current()
            updateWallpaper.invalidateHome()
            // The reference is being dropped, so the standing read grant that
            // went with it is dropped too — unless the lock wallpaper still
            // points at the same file.
            WallpaperUriPermissionPolicy.staleUri(previous, WallpaperSlot.HOME, newUri = null)
                ?.let(wallpaperController::releaseReadAccess)
            events.send("That wallpaper image is no longer available. Choose another in Settings.")
        }
    }

    /**
     * Long press on a pinned app removes it from home. Reversible from the
     * drawer, where the same long press pins it again.
     */
    fun unpin(app: LauncherApp) {
        viewModelScope.launch {
            updateFavorites.setFavorite(app.packageName, false)
            events.send("${app.appName} unpinned from home.")
        }
    }

    fun launch(app: LauncherApp) {
        val message = when (appLauncher.launch(app.packageName)) {
            AppLaunchResult.Launched -> {
                viewModelScope.launch { recordAppOpened(app.packageName) }
                null
            }
            AppLaunchResult.NoLaunchableActivity -> "${app.appName} has nothing to open."
            AppLaunchResult.NotInstalled -> "${app.appName} is no longer installed."
        }
        if (message != null) viewModelScope.launch { events.send(message) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
