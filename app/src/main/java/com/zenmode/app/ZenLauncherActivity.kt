package com.zenmode.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.feature.launcher.LauncherNavHost
import com.zenmode.app.system.launcher.WallpaperImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The home screen, when the user has chosen Zen Launcher as their launcher.
 *
 * Kept separate from [MainActivity] on purpose. The two have different jobs and
 * different lifecycles: MainActivity is the Zen Mode app, reached from the app
 * icon and from the blocker's redirect, with a normal back stack. This one is
 * the home app — pressing Home must always land on the same, ready screen.
 *
 * Keeping them apart also means nothing about Zen Mode changes for people who
 * never make this their launcher: the existing app behaves exactly as before.
 */
@AndroidEntryPoint
class ZenLauncherActivity : ComponentActivity() {

    @Inject lateinit var wallpaperImageLoader: WallpaperImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ZenModeTheme {
                LauncherApp(
                    onOpenZenMode = ::openZenMode,
                    onOpenLauncherSettings = ::openLauncherSettings,
                    imageLoader = wallpaperImageLoader,
                )
            }
        }
    }

    private fun openZenMode() {
        runCatching { startActivity(zenModeIntent()) }
    }

    /**
     * Opens the app's settings, where the launcher's own sections live —
     * including "Set as default launcher", which is how the user switches back
     * to another launcher.
     *
     * Reached by a long press on empty home space *and* by the SETTINGS button,
     * so the gesture is never the only way in.
     */
    private fun openLauncherSettings() {
        val intent = zenModeIntent()
            .putExtra(MainActivity.EXTRA_START_DESTINATION, MainActivity.DESTINATION_SETTINGS)
        runCatching { startActivity(intent) }
    }

    private fun zenModeIntent(): Intent = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

}

@Composable
private fun LauncherApp(
    onOpenZenMode: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    imageLoader: WallpaperImageLoader,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LauncherNavHost(
                onOpenZenMode = onOpenZenMode,
                onOpenLauncherSettings = onOpenLauncherSettings,
                onMessage = { message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                },
                imageLoader = imageLoader,
            )
        }
    }
}
