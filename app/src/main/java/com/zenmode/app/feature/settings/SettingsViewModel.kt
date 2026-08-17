package com.zenmode.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.ZenDuration
import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.domain.usecase.CheckAccessibilityPermissionUseCase
import com.zenmode.app.domain.usecase.ClearHistoryUseCase
import com.zenmode.app.domain.usecase.GetBlockedAppsUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.domain.usecase.UpdateSettingsUseCase
import com.zenmode.app.system.LockdownCapability
import com.zenmode.app.system.ZenLockdownController
import com.zenmode.app.domain.model.LockWallpaperCapability
import com.zenmode.app.domain.model.WallpaperSettings
import com.zenmode.app.domain.usecase.GetWallpaperSettingsUseCase
import com.zenmode.app.domain.usecase.UpdateWallpaperUseCase
import com.zenmode.app.system.launcher.DefaultLauncherChecker
import com.zenmode.app.system.launcher.WallpaperController
import com.zenmode.app.system.launcher.WallpaperImageLoader
import com.zenmode.app.system.launcher.WallpaperResult
import com.zenmode.app.system.launcher.WallpaperSlot
import com.zenmode.app.system.launcher.WallpaperUriPermissionPolicy
import com.zenmode.app.system.launcher.DefaultLauncherState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: ZenSettings = ZenSettings(),
    val blockedAppCount: Int = 0,
    val accessibilityEnabled: Boolean = false,
    /** What Android will actually enforce on this device if strict mode is on. */
    val lockdownCapability: LockdownCapability = LockdownCapability.SCREEN_PINNING,
    /** Whether Android currently opens this app when Home is pressed. */
    val defaultLauncherState: DefaultLauncherState = DefaultLauncherState.NOT_CHOSEN,
    val wallpaper: WallpaperSettings = WallpaperSettings(),
    /** Whether this device will accept a separate lock-screen wallpaper at all. */
    val lockWallpaperCapability: LockWallpaperCapability = LockWallpaperCapability.SUPPORTED,
    val durationOptions: List<Int> = ZenDuration.PRESET_MINUTES,
    val showClearHistoryConfirmation: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updateSettings: UpdateSettingsUseCase,
    private val clearHistory: ClearHistoryUseCase,
    private val lockdownController: ZenLockdownController,
    private val defaultLauncherChecker: DefaultLauncherChecker,
    private val updateWallpaper: UpdateWallpaperUseCase,
    private val wallpaperController: WallpaperController,
    private val wallpaperImageLoader: WallpaperImageLoader,
    private val getWallpaperSettings: GetWallpaperSettingsUseCase,
    getSettings: GetSettingsUseCase,
    getBlockedApps: GetBlockedAppsUseCase,
    checkAccessibilityPermission: CheckAccessibilityPermissionUseCase,
) : ViewModel() {

    private val clearHistoryConfirmationVisible = MutableStateFlow(false)
    private val refreshTrigger = MutableStateFlow(0)

    /** The Zen Mode half of the screen, kept separate so both combines stay typed. */
    private val zenSettingsState = combine(
        getSettings(),
        getBlockedApps.enabledPackages(),
        checkAccessibilityPermission(),
    ) { settings, blockedPackages, accessibilityEnabled ->
        Triple(settings, blockedPackages.size, accessibilityEnabled)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        zenSettingsState,
        getWallpaperSettings(),
        clearHistoryConfirmationVisible,
        refreshTrigger,
    ) { (settings, blockedAppCount, accessibilityEnabled), wallpaper, confirming, _ ->
        SettingsUiState(
            isLoading = false,
            settings = settings,
            blockedAppCount = blockedAppCount,
            accessibilityEnabled = accessibilityEnabled,
            lockdownCapability = lockdownController.capability(),
            defaultLauncherState = defaultLauncherChecker.state(),
            wallpaper = wallpaper,
            lockWallpaperCapability = wallpaperController.lockWallpaperCapability(),
            showClearHistoryConfirmation = confirming,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun setDefaultDuration(minutes: Int) = update { updateSettings.setDefaultDuration(minutes) }

    fun setConfirmStart(enabled: Boolean) = update { updateSettings.setConfirmStart(enabled) }

    fun setCompletionNotification(enabled: Boolean) =
        update { updateSettings.setCompletionNotification(enabled) }

    fun setPureBlackZenScreen(enabled: Boolean) =
        update { updateSettings.setPureBlackZenScreen(enabled) }

    fun setShowClock(enabled: Boolean) = update { updateSettings.setShowClock(enabled) }

    fun setShowDate(enabled: Boolean) = update { updateSettings.setShowDate(enabled) }

    fun setUse24HourClock(enabled: Boolean) = update { updateSettings.setUse24HourClock(enabled) }

    fun setShowCallButton(enabled: Boolean) = update { updateSettings.setShowCallButton(enabled) }

    fun setStrictMode(enabled: Boolean) = update { updateSettings.setStrictMode(enabled) }

    /**
     * Android does not broadcast a change of home app, so the launcher state is
     * re-read when the screen comes back into view.
     */
    fun refresh() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    /**
     * The system dialog that asks the user to make this the home app, or null
     * when the platform will not offer it. Android never lets the app decide
     * this for itself.
     */
    fun requestHomeRoleIntent() = defaultLauncherChecker.requestHomeRoleIntent()

    fun openHomeSettings(): Boolean = defaultLauncherChecker.openHomeSettings()

    /**
     * Records a chosen home image.
     *
     * Read access is persisted **first**, and the choice is stored only if that
     * worked: a URI the app cannot keep reading would stop resolving after a
     * reboot, leaving a stored reference that fails on every draw. Whatever the
     * previous image was then gives its grant back, so the app does not
     * accumulate standing read access to every picture the user ever tried.
     */
    fun onHomeWallpaperPicked(uri: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val previous = getWallpaperSettings.current()
            if (!wallpaperController.persistReadAccess(uri)) {
                onResult(ACCESS_NOT_KEPT)
                return@launch
            }
            wallpaperImageLoader.invalidate()
            updateWallpaper.setHome(uri)
            releaseStale(previous, WallpaperSlot.HOME, uri)
            onResult(null)
        }
    }

    fun onHomeWallpaperCleared() {
        viewModelScope.launch {
            val previous = getWallpaperSettings.current()
            wallpaperImageLoader.invalidate()
            updateWallpaper.clearHome()
            releaseStale(previous, WallpaperSlot.HOME, newUri = null)
        }
    }

    /**
     * Replaces the device's lock-screen wallpaper. A real, system-wide change,
     * so the outcome is reported rather than assumed.
     */
    fun onLockWallpaperPicked(uri: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val previous = getWallpaperSettings.current()
            if (!wallpaperController.persistReadAccess(uri)) {
                onResult(ACCESS_NOT_KEPT)
                return@launch
            }
            val message = when (val result = wallpaperController.applyLockWallpaper(uri)) {
                WallpaperResult.Applied -> {
                    updateWallpaper.setLock(uri)
                    releaseStale(previous, WallpaperSlot.LOCK, uri)
                    null
                }
                WallpaperResult.Unsupported ->
                    "This device does not allow the lock-screen wallpaper to be changed."
                WallpaperResult.ImageUnavailable -> "That image could not be opened."
                is WallpaperResult.Failed -> result.reason
            }
            // Nothing was stored, so the grant taken a moment ago has no job to
            // do and is handed straight back.
            if (message != null) releaseUnusedClaim(previous, uri)
            onResult(message)
        }
    }

    fun onLockWallpaperCleared(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val previous = getWallpaperSettings.current()
            val message = when (val result = wallpaperController.clearLockWallpaper()) {
                WallpaperResult.Applied -> {
                    updateWallpaper.clearLock()
                    releaseStale(previous, WallpaperSlot.LOCK, newUri = null)
                    null
                }
                WallpaperResult.Unsupported ->
                    "This device does not allow the lock-screen wallpaper to be changed."
                WallpaperResult.ImageUnavailable -> "That image could not be opened."
                is WallpaperResult.Failed -> result.reason
            }
            onResult(message)
        }
    }

    /** Gives back the grant the old image no longer needs, if it needs none. */
    private fun releaseStale(previous: WallpaperSettings, slot: WallpaperSlot, newUri: String?) {
        WallpaperUriPermissionPolicy.staleUri(previous, slot, newUri)
            ?.let(wallpaperController::releaseReadAccess)
    }

    private fun releaseUnusedClaim(current: WallpaperSettings, uri: String) {
        WallpaperUriPermissionPolicy.unusedClaim(current, uri)
            ?.let(wallpaperController::releaseReadAccess)
    }

    fun onClearHistoryRequested() {
        clearHistoryConfirmationVisible.value = true
    }

    fun onClearHistoryDismissed() {
        clearHistoryConfirmationVisible.value = false
    }

    fun onClearHistoryConfirmed() {
        clearHistoryConfirmationVisible.value = false
        update { clearHistory() }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Said plainly rather than failing silently: the picker closed, so the
         * user has every reason to think the wallpaper changed.
         */
        const val ACCESS_NOT_KEPT =
            "Android would not grant lasting access to that image, so it was not saved. " +
                "Try another image, or one stored on this device."
    }
}
