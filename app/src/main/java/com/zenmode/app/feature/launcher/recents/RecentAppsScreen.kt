package com.zenmode.app.feature.launcher.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.core.designsystem.ZenDivider
import com.zenmode.app.core.designsystem.ZenEmptyState
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenSurfaceElevated
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.domain.model.LauncherRecentApp
import com.zenmode.app.feature.common.AppIcon

object RecentAppsTestTags {
    const val SCREEN = "recents_screen"
    const val LIST = "recents_list"
    const val EMPTY = "recents_empty"
    const val CLEAR = "recents_clear"
    const val EXPLANATION = "recents_explanation"
    fun card(packageName: String) = "recents_card_$packageName"
}

@Composable
fun RecentAppsRoute(
    onSessionStarted: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: RecentAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Refreshed when the screen opens and whenever the launcher resumes — never
    // on a timer.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is RecentAppsEvent.Error -> onMessage(event.message)
                RecentAppsEvent.SessionStarted -> onSessionStarted()
            }
        }
    }

    // A session starting closes this screen; the Zen presentation is what the
    // phone shows during a session.
    LaunchedEffect(state.sessionActive) {
        if (state.sessionActive) onSessionStarted()
    }

    RecentAppsScreen(
        state = state,
        onOpen = viewModel::open,
        onRemove = viewModel::removeFromList,
        onClearAll = viewModel::clearAll,
    )
}

/**
 * The launcher's recent-app list, as a stack of cards (launcher spec §8).
 *
 * **This is not Android's Recents, and it does not pretend to be.** Android
 * gives a third-party launcher no way to enumerate other apps' tasks, no task
 * previews, and no way to close another app's task. What it shows is the apps
 * this launcher opened, most recent first, and the screen says so.
 *
 * The cards therefore carry an icon and a name on a plain card rather than a
 * screenshot. That is the honest fallback: no app is ever captured or recorded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentAppsScreen(
    state: RecentAppsUiState,
    onOpen: (LauncherRecentApp) -> Unit,
    onRemove: (LauncherRecentApp) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenBlack)
            .systemBarsPadding()
            .testTag(RecentAppsTestTags.SCREEN),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ZenSectionHeader("Recently opened")
            Text(
                // Said plainly, so nobody mistakes this for the system's list.
                text = "Apps you opened from Zen Launcher. Android does not let an app read " +
                    "the system's recent apps or their previews.",
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
                modifier = Modifier.testTag(RecentAppsTestTags.EXPLANATION),
            )
        }

        if (state.isEmpty) {
            ZenEmptyState(
                title = "No recent apps",
                description = "Apps you open from the launcher will appear here.",
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag(RecentAppsTestTags.EMPTY),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // Weighted, so the cards scroll within the space left and the
                // clear action stays reachable at the bottom.
                .weight(1f)
                .testTag(RecentAppsTestTags.LIST),
            // Negative spacing is what makes the cards overlap into a stack.
            verticalArrangement = Arrangement.spacedBy(CARD_OVERLAP),
            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        ) {
            itemsIndexed(items = state.apps, key = { _, app -> app.packageName }) { index, app ->
                RecentCard(
                    app = app,
                    index = index,
                    total = state.apps.size,
                    showIcon = showIcons,
                    onOpen = { onOpen(app) },
                    onRemove = { onRemove(app) },
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ZenSecondaryButton(
                text = "CLEAR LIST",
                onClick = onClearAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RecentAppsTestTags.CLEAR),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One card in the stack.
 *
 * Earlier cards are drawn above later ones and inset a little less, so the most
 * recent app reads as the front of the stack. No shadows or gradients — depth
 * comes from overlap and a one-pixel border, in keeping with the rest of the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(
    app: LauncherRecentApp,
    index: Int,
    total: Int,
    showIcon: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val depthInset = (CARD_DEPTH_INSET * index.coerceAtMost(MAX_VISUAL_DEPTH))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp + depthInset, end = 24.dp + depthInset)
            // Most recent on top of the ones behind it.
            .zIndex((total - index).toFloat())
            .clip(RoundedCornerShape(20.dp))
            .background(ZenSurfaceElevated)
            .border(1.dp, ZenDivider, RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onRemove)
            .semantics(mergeDescendants = true) {
                contentDescription = "${app.appName}, recently opened"
                // TalkBack reaches removal without needing a long press.
                customActions = listOf(
                    CustomAccessibilityAction(label = "Open ${app.appName}") {
                        onOpen()
                        true
                    },
                    CustomAccessibilityAction(label = "Remove ${app.appName} from this list") {
                        onRemove()
                        true
                    },
                )
            }
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag(RecentAppsTestTags.card(app.packageName)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            AppIcon(packageName = app.packageName, size = 40)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                color = ZenTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Long press to remove",
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
            )
        }
    }
}

/** How far cards overlap. Enough to read as a stack, not enough to hide names. */
private val CARD_OVERLAP = (-16).dp

/** Each card further back is inset slightly, suggesting depth without shadows. */
private val CARD_DEPTH_INSET = 6.dp

/** Beyond this the inset would eat the card, so depth stops accumulating. */
private const val MAX_VISUAL_DEPTH = 3
