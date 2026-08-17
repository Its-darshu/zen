package com.zenmode.app.feature.blockedapps

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
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.core.designsystem.ZenDivider
import com.zenmode.app.core.designsystem.ZenEmptyState
import com.zenmode.app.core.designsystem.ZenHorizontalDivider
import com.zenmode.app.core.designsystem.ZenMinTouchTarget
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenSurfaceElevated
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.designsystem.ZenTopBar
import com.zenmode.app.domain.model.SelectableApp
import com.zenmode.app.feature.common.AppIcon

object BlockedAppsTestTags {
    const val SCREEN = "blocked_apps_screen"
    const val SEARCH = "blocked_apps_search"
    const val SELECT_ALL = "blocked_apps_select_all"
    const val CLEAR = "blocked_apps_clear"
    const val LIST = "blocked_apps_list"
    const val EMPTY = "blocked_apps_empty"
    const val COUNT = "blocked_apps_count"
    fun app(packageName: String) = "blocked_app_$packageName"
}

@Composable
fun BlockedAppsRoute(
    onBack: () -> Unit,
    viewModel: BlockedAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BlockedAppsScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::setQuery,
        onToggleApp = viewModel::setBlocked,
        onSelectAll = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection,
    )
}

@Composable
fun BlockedAppsScreen(
    state: BlockedAppsUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleApp: (SelectableApp, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .testTag(BlockedAppsTestTags.SCREEN),
    ) {
        ZenTopBar(title = "Blocked apps", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "${state.selectedCount} selected",
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
                modifier = Modifier.testTag(BlockedAppsTestTags.COUNT),
            )
            Spacer(Modifier.height(12.dp))

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
                    .semantics { contentDescription = "Search apps" }
                    .testTag(BlockedAppsTestTags.SEARCH),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZenSecondaryButton(
                    text = "SELECT ALL",
                    onClick = onSelectAll,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(BlockedAppsTestTags.SELECT_ALL),
                )
                ZenSecondaryButton(
                    text = "CLEAR",
                    onClick = onClearSelection,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(BlockedAppsTestTags.CLEAR),
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        if (state.isEmpty) {
            ZenEmptyState(
                title = if (state.query.isBlank()) "No apps to show" else "No apps match “${state.query}”",
                description = if (state.query.isBlank()) {
                    "Apps you can open from the launcher will appear here. " +
                        "The dialer, the launcher and Android settings are never blocked."
                } else {
                    null
                },
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag(BlockedAppsTestTags.EMPTY),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BlockedAppsTestTags.LIST),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 48.dp),
        ) {
            items(items = state.apps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    showIcon = showIcons,
                    onToggle = { blocked -> onToggleApp(app, blocked) },
                )
                ZenHorizontalDivider()
            }
        }
    }
}

@Composable
private fun AppRow(
    app: SelectableApp,
    showIcon: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ZenMinTouchTarget + 16.dp)
            .clickable(role = Role.Switch) { onToggle(!app.isBlocked) }
            .semantics(mergeDescendants = true) {
                contentDescription = "${app.appName}, ${if (app.isBlocked) "blocked" else "not blocked"}"
            }
            .padding(vertical = 8.dp)
            .testTag(BlockedAppsTestTags.app(app.packageName)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            AppIcon(packageName = app.packageName)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = ZenTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = app.isBlocked,
            onCheckedChange = onToggle,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZenBlack,
                checkedTrackColor = ZenTextPrimary,
                checkedBorderColor = ZenTextPrimary,
                uncheckedThumbColor = ZenTextSecondary,
                uncheckedTrackColor = ZenSurfaceElevated,
                uncheckedBorderColor = ZenDivider,
            ),
        )
    }
}
