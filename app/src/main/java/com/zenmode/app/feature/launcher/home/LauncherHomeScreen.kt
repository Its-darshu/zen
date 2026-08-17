package com.zenmode.app.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.core.designsystem.ZenMinTouchTarget
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.designsystem.ZenTimerTextStyle
import com.zenmode.app.domain.model.LauncherApp
import com.zenmode.app.feature.common.AppIcon
import com.zenmode.app.feature.launcher.gesture.LauncherGestureRules
import com.zenmode.app.feature.launcher.gesture.launcherLongPress
import com.zenmode.app.feature.launcher.gesture.launcherSwipeUp
import com.zenmode.app.feature.launcher.gesture.rememberReservedEdges
import com.zenmode.app.system.launcher.WallpaperImageLoader
import androidx.compose.runtime.LaunchedEffect

object LauncherHomeTestTags {
    const val SCREEN = "launcher_home_screen"
    const val CLOCK = "launcher_clock"
    const val DATE = "launcher_date"
    const val SESSION = "launcher_session"
    const val OPEN_ZEN = "launcher_open_zen"
    const val OPEN_SETTINGS = "launcher_open_settings"
    const val OPEN_DRAWER = "launcher_open_drawer"
    const val OPEN_RECENTS = "launcher_open_recents"
    const val FAVORITES = "launcher_favorites"
    fun favorite(packageName: String) = "launcher_favorite_$packageName"
}

@Composable
fun LauncherHomeRoute(
    onOpenZenMode: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenRecents: () -> Unit,
    onMessage: (String) -> Unit,
    imageLoader: WallpaperImageLoader,
    viewModel: LauncherHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect(onMessage)
    }

    LauncherHomeScreen(
        state = state,
        onOpenZenMode = onOpenZenMode,
        onOpenLauncherSettings = onOpenLauncherSettings,
        onOpenAppDrawer = onOpenAppDrawer,
        onOpenRecents = onOpenRecents,
        onLaunchFavorite = viewModel::launch,
        onUnpinFavorite = viewModel::unpin,
        imageLoader = imageLoader,
        onWallpaperUnavailable = viewModel::onWallpaperUnavailable,
    )
}

/**
 * The launcher home screen (launcher spec §3, §5).
 *
 * Black, a clock, a date, and the apps the user pinned — the same monochrome
 * language as the rest of the app. Swiping up or tapping APPS opens the drawer.
 *
 * While a Zen session runs the drawer entry and the favourites are gone: the
 * session is what the phone is for at that moment, and a home screen offering a
 * grid of apps over a running session would undo it. Blocking itself stays
 * where it already lives — this screen adds no second opinion about what may
 * run.
 */
@Composable
fun LauncherHomeScreen(
    state: LauncherHomeUiState,
    onOpenZenMode: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenRecents: () -> Unit = {},
    onLaunchFavorite: (LauncherApp) -> Unit,
    onUnpinFavorite: (LauncherApp) -> Unit = {},
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
    imageLoader: WallpaperImageLoader? = null,
    onWallpaperUnavailable: () -> Unit = {},
) {
    WallpaperBackgroundOrBlack(
        wallpaperUri = state.wallpaperUri,
        imageLoader = imageLoader,
        onUnavailable = onWallpaperUnavailable,
        modifier = modifier,
    ) {
    val reservedEdges = rememberReservedEdges()
    val swipeThresholdPx = with(LocalDensity.current) {
        LauncherGestureRules.SwipeThreshold.toPx()
    }
    // Gestures exist only when there is something for them to reach. During a
    // session there is no drawer and no settings to open, so nothing listens.
    val gesturesEnabled = !state.sessionActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .launcherSwipeUp(
                enabled = gesturesEnabled,
                reservedEdges = reservedEdges,
                thresholdPx = swipeThresholdPx,
                onSwipeUp = onOpenAppDrawer,
            )
            .launcherLongPress(
                enabled = gesturesEnabled,
                reservedEdges = reservedEdges,
                onLongPress = onOpenLauncherSettings,
            )
            .systemBarsPadding()
            .padding(horizontal = 32.dp)
            .testTag(LauncherHomeTestTags.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))

        Text(
            text = state.clockText,
            style = MaterialTheme.typography.displayLarge,
            color = ZenTextPrimary,
            modifier = Modifier.testTag(LauncherHomeTestTags.CLOCK),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.dateText,
            style = MaterialTheme.typography.labelMedium,
            color = ZenTextSecondary,
            modifier = Modifier.testTag(LauncherHomeTestTags.DATE),
        )

        if (state.sessionActive) {
            Spacer(Modifier.height(48.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.testTag(LauncherHomeTestTags.SESSION),
            ) {
                Text(
                    text = state.remainingText,
                    style = ZenTimerTextStyle,
                    color = ZenTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Z E N   M O D E",
                    style = MaterialTheme.typography.labelLarge,
                    color = ZenTextSecondary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (!state.sessionActive && state.favorites.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LauncherHomeTestTags.FAVORITES),
            ) {
                state.favorites.forEach { app ->
                    FavoriteRow(
                        app = app,
                        showIcon = showIcons,
                        onClick = { onLaunchFavorite(app) },
                        onLongClick = { onUnpinFavorite(app) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (!state.sessionActive) {
            // Two rows rather than four cramped buttons: every launcher-owned
            // screen stays reachable without a gesture, and each target keeps a
            // comfortable touch size at large font sizes.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZenSecondaryButton(
                    text = "APPS",
                    onClick = onOpenAppDrawer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LauncherHomeTestTags.OPEN_DRAWER),
                )
                ZenSecondaryButton(
                    text = "RECENT",
                    onClick = onOpenRecents,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LauncherHomeTestTags.OPEN_RECENTS),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZenSecondaryButton(
                    text = "ZEN MODE",
                    onClick = onOpenZenMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LauncherHomeTestTags.OPEN_ZEN),
                )
                // Does exactly what the long press does, so the gesture is
                // never the only way to reach launcher settings.
                ZenSecondaryButton(
                    text = "SETTINGS",
                    onClick = onOpenLauncherSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LauncherHomeTestTags.OPEN_SETTINGS),
                )
            }
        } else {
            ZenSecondaryButton(
                text = "ZEN MODE",
                onClick = onOpenZenMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LauncherHomeTestTags.OPEN_ZEN),
            )
        }

        Spacer(Modifier.height(40.dp))
    }
    }
}

/**
 * Wallpaper behind the content, or plain black.
 *
 * The loader is optional so the screen can be rendered in tests, and on a
 * running session there is never a wallpaper to draw at all.
 */
@Composable
private fun WallpaperBackgroundOrBlack(
    wallpaperUri: String?,
    imageLoader: WallpaperImageLoader?,
    onUnavailable: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (wallpaperUri != null && imageLoader != null) {
        WallpaperBackground(
            wallpaperUri = wallpaperUri,
            imageLoader = imageLoader,
            onUnavailable = onUnavailable,
            modifier = modifier,
            content = content,
        )
    } else {
        Box(modifier = modifier.fillMaxSize().background(ZenBlack)) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    app: LauncherApp,
    showIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ZenMinTouchTarget)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Open ${app.appName}. Long press to unpin from home."
            }
            .padding(vertical = 8.dp)
            .testTag(LauncherHomeTestTags.favorite(app.packageName)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            AppIcon(packageName = app.packageName, size = 32)
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            color = ZenTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
