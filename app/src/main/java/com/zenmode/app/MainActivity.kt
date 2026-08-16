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

        setContent {
            ZenModeTheme {
                ZenApp(onCall = { callLauncher.openDialer() })
            }
        }
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
    viewModel: ZenAppViewModel = hiltViewModel(),
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val shouldHoldDevice by viewModel.shouldHoldDevice.collectAsStateWithLifecycle()
    val sessionActive by viewModel.sessionActive.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    RequestNotificationPermissionOnce()
    HoldDeviceWhileStrictSessionRuns(shouldHoldDevice)
    ShowOverLockScreenWhileSessionRuns(sessionActive)

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
                    startDestination = state.startRoute,
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

/**
 * Lets the Zen screen appear when the screen wakes during a session.
 *
 * This does **not** replace or weaken the lock screen: on a device with a
 * secure keyguard the user still unlocks normally, and can still leave. It only
 * means waking the phone shows the session rather than hiding it.
 */
@Composable
private fun ShowOverLockScreenWhileSessionRuns(sessionActive: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return

    LaunchedEffect(sessionActive) {
        activity.setShowWhenLocked(sessionActive)
        activity.setTurnScreenOn(sessionActive)
    }
}

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
