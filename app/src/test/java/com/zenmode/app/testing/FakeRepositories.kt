package com.zenmode.app.testing

import com.zenmode.app.domain.model.BlockedApp
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.permission.AccessibilityPermissionMonitor
import com.zenmode.app.domain.repository.BlockedAppRepository
import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.domain.repository.SessionRepository
import com.zenmode.app.domain.repository.SettingsRepository
import com.zenmode.app.domain.repository.ZenModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins for the repositories.
 *
 * They back onto one shared [SessionStore] so a use case that starts a session
 * through [FakeZenModeRepository] can be checked through [FakeSessionRepository]
 * exactly as it would be against the real database — including the "at most one
 * active session" invariant.
 */
class SessionStore {
    val sessions = MutableStateFlow<List<ZenSession>>(emptyList())
    private var nextId = 1L

    fun insert(session: ZenSession): ZenSession {
        val stored = session.copy(id = nextId++)
        sessions.value = sessions.value + stored
        return stored
    }

    fun replace(session: ZenSession) {
        sessions.value = sessions.value.map { if (it.id == session.id) session else it }
    }

    fun activeOrNull(): ZenSession? = sessions.value.lastOrNull { it.status == SessionStatus.ACTIVE }

    fun seed(vararg seeded: ZenSession) {
        seeded.forEach { insert(it) }
    }
}

class FakeZenModeRepository(private val store: SessionStore = SessionStore()) : ZenModeRepository {

    override fun observeActiveSession(): Flow<ZenSession?> =
        store.sessions.map { sessions -> sessions.lastOrNull { it.status == SessionStatus.ACTIVE } }

    override suspend fun getActiveSession(): ZenSession? = store.activeOrNull()

    override suspend fun startSession(session: ZenSession): ZenSession? {
        require(session.status == SessionStatus.ACTIVE)
        if (store.activeOrNull() != null) return null
        return store.insert(session)
    }

    override suspend fun finishActiveSession(
        status: SessionStatus,
        endedAt: Long,
        actualDurationSeconds: Long,
    ): ZenSession? {
        require(status.isTerminal)
        val active = store.activeOrNull() ?: return null
        val finished = active.copy(
            status = status,
            endedAt = endedAt,
            actualDurationSeconds = actualDurationSeconds,
        )
        store.replace(finished)
        return finished
    }

    override suspend fun updateActiveBlockedAppCount(count: Int) {
        val active = store.activeOrNull() ?: return
        store.replace(active.copy(blockedAppCount = count))
    }
}

class FakeSessionRepository(private val store: SessionStore = SessionStore()) : SessionRepository {

    override fun observeSessions(filter: SessionFilter): Flow<List<ZenSession>> =
        store.sessions.map { sessions ->
            sessions
                .filter { session ->
                    when (filter) {
                        SessionFilter.ALL -> true
                        SessionFilter.COMPLETED -> session.status == SessionStatus.COMPLETED
                        SessionFilter.CANCELLED -> session.status == SessionStatus.CANCELLED
                    }
                }
                .sortedByDescending { it.startedAt }
        }

    override fun observeCompletedSessions(): Flow<List<ZenSession>> =
        observeSessions(SessionFilter.COMPLETED)

    override fun observeSessionsStartedBetween(from: Long, to: Long): Flow<List<ZenSession>> =
        store.sessions.map { sessions ->
            sessions
                .filter { it.startedAt >= from && it.startedAt < to }
                .sortedByDescending { it.startedAt }
        }

    override suspend fun getSession(id: Long): ZenSession? =
        store.sessions.value.firstOrNull { it.id == id }

    override suspend fun deleteSession(id: Long) {
        store.sessions.value = store.sessions.value.filterNot { it.id == id }
    }

    override suspend fun clearHistory() {
        store.sessions.value = emptyList()
    }
}

class FakeBlockedAppRepository(
    initial: List<BlockedApp> = emptyList(),
) : BlockedAppRepository {

    private val apps = MutableStateFlow(initial)

    override fun observeBlockedApps(): Flow<List<BlockedApp>> =
        apps.map { list -> list.sortedBy { it.appName.lowercase() } }

    override fun observeEnabledPackages(): Flow<Set<String>> =
        apps.map { list -> list.filter { it.enabled }.map { it.packageName }.toSet() }

    override suspend fun getEnabledPackages(): Set<String> =
        apps.value.filter { it.enabled }.map { it.packageName }.toSet()

    override suspend fun countEnabled(): Int = apps.value.count { it.enabled }

    override suspend fun setBlocked(packageName: String, appName: String, enabled: Boolean) {
        val updated = BlockedApp(packageName, appName, enabled)
        apps.value = apps.value.filterNot { it.packageName == packageName } + updated
    }

    override suspend fun setBlockedApps(apps: List<BlockedApp>) {
        if (apps.isEmpty()) return
        val updatedPackages = apps.map { it.packageName }.toSet()
        this.apps.value = this.apps.value.filterNot { it.packageName in updatedPackages } + apps
    }

    override suspend fun clearSelection() {
        apps.value = apps.value.map { it.copy(enabled = false) }
    }

    override suspend fun removeUninstalled(installedPackages: Set<String>) {
        if (installedPackages.isEmpty()) return
        apps.value = apps.value.filter { it.packageName in installedPackages }
    }
}

class FakeAccessibilityPermissionMonitor(enabled: Boolean = false) : AccessibilityPermissionMonitor {

    private val state = MutableStateFlow(enabled)

    override val isEnabled: Flow<Boolean> = state

    override suspend fun isEnabledNow(): Boolean = state.value

    fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }
}

class FakeSettingsRepository(initial: ZenSettings = ZenSettings()) : SettingsRepository {

    private val settings = MutableStateFlow(initial)

    override fun observeSettings(): Flow<ZenSettings> = settings

    override suspend fun getSettings(): ZenSettings = settings.value

    override suspend fun setDefaultDurationMinutes(minutes: Int) {
        settings.value = settings.value.copy(defaultDurationMinutes = minutes)
    }

    override suspend fun setConfirmStart(enabled: Boolean) {
        settings.value = settings.value.copy(confirmStart = enabled)
    }

    override suspend fun setCompletionNotification(enabled: Boolean) {
        settings.value = settings.value.copy(completionNotification = enabled)
    }

    override suspend fun setPureBlackZenScreen(enabled: Boolean) {
        settings.value = settings.value.copy(pureBlackZenScreen = enabled)
    }

    override suspend fun setShowClock(enabled: Boolean) {
        settings.value = settings.value.copy(showClock = enabled)
    }

    override suspend fun setShowDate(enabled: Boolean) {
        settings.value = settings.value.copy(showDate = enabled)
    }

    override suspend fun setUse24HourClock(enabled: Boolean) {
        settings.value = settings.value.copy(use24HourClock = enabled)
    }

    override suspend fun setShowCallButton(enabled: Boolean) {
        settings.value = settings.value.copy(showCallButton = enabled)
    }

    override suspend fun setStrictMode(enabled: Boolean) {
        settings.value = settings.value.copy(strictMode = enabled)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        settings.value = settings.value.copy(onboardingCompleted = completed)
    }

    override suspend fun resetToDefaults() {
        settings.value = ZenSettings(onboardingCompleted = settings.value.onboardingCompleted)
    }
}
