package com.zenmode.app

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.navigation.AppStartState
import com.zenmode.app.navigation.ZenAppViewModel
import com.zenmode.app.navigation.ZenNavHost
import com.zenmode.app.navigation.ZenRoute
import com.zenmode.app.system.CallLauncher
import com.zenmode.app.system.LockdownAction
import com.zenmode.app.system.LockdownPolicy
import com.zenmode.app.system.ZenLockdownController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var callLauncher: CallLauncher

    @Inject lateinit var lockdownController: ZenLockdownController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Does nothing unless this app has been provisioned as device owner.
        lockdownController.applyDeviceOwnerPoliciesIfPossible()

        val requestedDestination = intent?.getStringExtra(EXTRA_START_DESTINATION)

        setContent {
            ZenModeTheme {
                ZenApp(
                    onCall = { callLauncher.openDialer() },
                    requestedStartDestination = requestedDestination,
                )
            }
        }
    }

    companion object {
        /**
         * Lets Zen Launcher open a specific screen — currently only settings.
         *
         * Absent for every other entry point, so the app icon and the blocker's
         * redirect behave exactly as they always have.
         */
        const val EXTRA_START_DESTINATION = "com.zenmode.app.extra.START_DESTINATION"

        const val DESTINATION_SETTINGS = "settings"
    }

    /**
     * Releases the device if the activity is going away for good.
     *
     * Without this, a crash or a swipe-away could leave Android holding a task
     * that nothing is going to release.
     */
    override fun onDestroy() {
        if (isFinishing) lockdownController.exit(this)
        super.onDestroy()
    }
}

@Composable
private fun ZenApp(
    onCall: () -> Boolean,
    requestedStartDestination: String? = null,
    viewModel: ZenAppViewModel = hiltViewModel(),
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val shouldHoldDevice by viewModel.shouldHoldDevice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    RequestNotificationPermissionOnce()
    HoldDeviceWhileStrictSessionRuns(shouldHoldDevice)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // A black screen for the moment it takes to read the active session:
            // better than showing home and yanking the user to the Zen screen.
            when (val state = startState) {
                AppStartState.Loading -> Unit
                is AppStartState.Ready -> ZenNavHost(
                    // A running session always wins: being sent to settings must
                    // never take the user off an active Zen screen.
                    startDestination = resolveStartDestination(
                        computed = state.startRoute,
                        requested = requestedStartDestination,
                    ),
                    onCall = {
                        if (!onCall()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "No dialer app is available on this device.",
                                )
                            }
                        }
                    },
                    onMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}

/**
 * Asks Android to hold this task while a strict-mode session runs, and to let
 * go the moment it does not.
 *
 * How much holding means is the platform's call: a kiosk on a provisioned
 * device, ordinary screen pinning everywhere else. Either way the app stops
 * holding as soon as the session ends.
 */
@Composable
private fun HoldDeviceWhileStrictSessionRuns(shouldHold: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val controller = rememberLockdownController() ?: return

    LaunchedEffect(shouldHold) {
        val action = LockdownPolicy.decide(
            sessionActive = shouldHold,
            strictModeEnabled = shouldHold,
            capability = controller.capability(),
            currentlyLocked = controller.isLockTaskActive(),
        )
        when (action) {
            LockdownAction.ENTER -> controller.enter(activity)
            LockdownAction.EXIT -> controller.exit(activity)
            LockdownAction.NONE -> Unit
        }
    }
}

/*
 * Deliberately absent: `setShowWhenLocked(true)`.
 *
 * It was tried, and it is a lock-screen bypass. Showing this activity above the
 * keyguard let the device be woken with the power button and reach the home
 * screen with no fingerprint or PIN at all — anyone picking up the phone during
 * a session got straight in.
 *
 * A focus app must never weaken the lock screen. The keyguard is the user's
 * security, not an obstacle to a session, and Zen Mode leaves it completely
 * alone: waking the phone shows the normal lock screen, and the session is
 * behind it like everything else.
 *
 * Strict mode is what keeps a session enforced after an unlock — the launcher
 * and every non-essential app are redirected back to the Zen screen.
 */

@Composable
private fun rememberLockdownController(): ZenLockdownController? {
    val activity = LocalContext.current.findActivity() as? MainActivity ?: return null
    return activity.lockdownController
}

private fun android.content.Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Asks for notification permission once, on Android 13+.
 *
 * Only the ongoing-session and completion notifications depend on it. Declining
 * costs nothing else: sessions run, end on time and are recorded either way.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Either answer is fine; nothing is blocked by it. */ },
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Honours a requested destination only when the app would otherwise have opened
 * on Home. Onboarding still comes first on a fresh install, and an active
 * session still wins over everything.
 */
private fun resolveStartDestination(computed: String, requested: String?): String = when {
    requested == null -> computed
    computed != ZenRoute.HOME -> computed
    requested == MainActivity.DESTINATION_SETTINGS -> ZenRoute.SETTINGS
    else -> computed
}
