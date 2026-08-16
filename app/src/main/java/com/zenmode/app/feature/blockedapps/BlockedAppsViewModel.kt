package com.zenmode.app.feature.blockedapps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.SelectableApp
import com.zenmode.app.domain.usecase.GetSelectableAppsUseCase
import com.zenmode.app.domain.usecase.UpdateBlockedAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<SelectableApp> = emptyList(),
    val query: String = "",
    val selectedCount: Int = 0,
) {
    val isEmpty: Boolean get() = apps.isEmpty()
}

@HiltViewModel
class BlockedAppsViewModel @Inject constructor(
    private val updateBlockedApps: UpdateBlockedAppsUseCase,
    getSelectableApps: GetSelectableAppsUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val allApps = getSelectableApps()
        // An app the user had blocked that is no longer installed is dropped
        // here, where we know what is installed (specification §35).
        .onEach { apps -> updateBlockedApps.removeUninstalled(apps.map { it.packageName }.toSet()) }

    val uiState: StateFlow<BlockedAppsUiState> = combine(allApps, query) { apps, currentQuery ->
        BlockedAppsUiState(
            isLoading = false,
            apps = GetSelectableAppsUseCase.search(apps, currentQuery),
            query = currentQuery,
            selectedCount = apps.count { it.isBlocked },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BlockedAppsUiState(),
    )

    fun setQuery(query: String) {
        this.query.value = query
    }

    fun setBlocked(app: SelectableApp, blocked: Boolean) {
        viewModelScope.launch {
            updateBlockedApps.setBlocked(app.packageName, app.appName, blocked)
        }
    }

    /** Switches on every app currently listed — the search results, if filtering. */
    fun selectAllVisible() {
        val visible = uiState.value.apps
        if (visible.isEmpty()) return
        viewModelScope.launch {
            updateBlockedApps.setSelection(
                visible.map { BlockedApp(it.packageName, it.appName, enabled = true) },
            )
        }
    }

    fun clearSelection() {
        viewModelScope.launch { updateBlockedApps.clearSelection() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
