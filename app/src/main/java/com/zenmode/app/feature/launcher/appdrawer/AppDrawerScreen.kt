package com.zenmode.app.feature.launcher.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.core.designsystem.ZenDivider
import com.zenmode.app.core.designsystem.ZenEmptyState
import com.zenmode.app.core.designsystem.ZenMinTouchTarget
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.domain.model.LauncherApp
import com.zenmode.app.feature.common.AppIcon

object AppDrawerTestTags {
    const val SCREEN = "app_drawer_screen"
    const val SEARCH = "app_drawer_search"
    const val LIST = "app_drawer_list"
    const val EMPTY = "app_drawer_empty"
    fun app(packageName: String) = "app_drawer_app_$packageName"
}

@Composable
fun AppDrawerRoute(
    onSessionStarted: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: AppDrawerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AppDrawerEvent.Error -> onMessage(event.message)
                AppDrawerEvent.SessionStarted -> onSessionStarted()
            }
        }
    }

    // A session starting while the drawer is open closes it. Zen Mode's own
    // blocking is still what stops apps; this only stops the launcher from
    // presenting a wall of apps over a running session.
    LaunchedEffect(state.sessionActive) {
        if (state.sessionActive) onSessionStarted()
    }

    AppDrawerScreen(
        state = state,
        onQueryChange = viewModel::setQuery,
        onLaunch = viewModel::launch,
        onToggleFavorite = viewModel::toggleFavorite,
    )
}

/**
 * The app drawer (launcher spec §5).
 *
 * Alphabetical, lazily rendered, and filtered in memory — the package manager
 * is read once when the screen opens, never per keystroke. Long-pressing a row
 * pins or unpins it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    state: AppDrawerUiState,
    onQueryChange: (String) -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenBlack)
            .systemBarsPadding()
            .testTag(AppDrawerTestTags.SCREEN),
    ) {
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "Search apps",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZenTextSecondary,
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ZenTextPrimary,
                unfocusedTextColor = ZenTextPrimary,
                focusedBorderColor = ZenTextSecondary,
                unfocusedBorderColor = ZenDivider,
                cursorColor = ZenTextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .semantics { contentDescription = "Search apps" }
                .testTag(AppDrawerTestTags.SEARCH),
        )

        Spacer(Modifier.height(8.dp))

        if (state.isEmpty) {
            ZenEmptyState(
                title = if (state.query.isBlank()) {
                    "No apps to show"
                } else {
                    "No apps match “${state.query}”"
                },
                description = if (state.query.isBlank()) {
                    "Apps you can open will appear here."
                } else {
                    null
                },
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag(AppDrawerTestTags.EMPTY),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AppDrawerTestTags.LIST),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 48.dp),
        ) {
            items(items = state.apps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    showIcon = showIcons,
                    onClick = { onLaunch(app) },
                    onLongClick = { onToggleFavorite(app) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: LauncherApp,
    showIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ZenMinTouchTarget + 16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(app.appName)
                    if (app.isFavorite) append(", pinned to home")
                    append(". Long press to ")
                    append(if (app.isFavorite) "unpin." else "pin to home.")
                }
            }
            .padding(vertical = 8.dp)
            .testTag(AppDrawerTestTags.app(app.packageName)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (showIcon) {
            AppIcon(packageName = app.packageName)
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            color = ZenTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (app.isFavorite) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "PINNED",
                style = MaterialTheme.typography.labelMedium,
                color = ZenTextSecondary,
            )
        }
    }
}
