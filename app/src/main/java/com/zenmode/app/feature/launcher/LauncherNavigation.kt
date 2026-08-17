package com.zenmode.app.feature.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zenmode.app.feature.launcher.appdrawer.AppDrawerRoute
import com.zenmode.app.feature.launcher.home.LauncherHomeRoute
import com.zenmode.app.feature.launcher.recents.RecentAppsRoute
import com.zenmode.app.system.launcher.WallpaperImageLoader

/** The launcher's own destinations, separate from the Zen Mode app's graph. */
object LauncherRoute {
    const val HOME = "launcher/home"
    const val APP_DRAWER = "launcher/apps"
    const val RECENTS = "launcher/recents"
}

/**
 * Navigation inside the home app.
 *
 * Only two screens, and the back behaviour differs between them: the drawer
 * goes back to home, and home goes nowhere, because there is nothing behind a
 * home screen. That is standard launcher behaviour — Home, Recents and Settings
 * all keep working.
 */
@Composable
fun LauncherNavHost(
    onOpenZenMode: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onMessage: (String) -> Unit,
    imageLoader: WallpaperImageLoader,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = LauncherRoute.HOME) {

        composable(
            route = LauncherRoute.HOME,
            // The home screen stays still; the drawer is what moves over it.
            enterTransition = { fadeIn(animationSpec = tween(DRAWER_ANIMATION_MILLIS)) },
            exitTransition = { fadeOut(animationSpec = tween(DRAWER_ANIMATION_MILLIS)) },
        ) {
            BackHandler(enabled = true) { /* Nothing sits behind the home screen. */ }

            LauncherHomeRoute(
                onOpenZenMode = onOpenZenMode,
                onOpenLauncherSettings = onOpenLauncherSettings,
                onOpenAppDrawer = { navController.navigate(LauncherRoute.APP_DRAWER) },
                onOpenRecents = { navController.navigate(LauncherRoute.RECENTS) },
                onMessage = onMessage,
                imageLoader = imageLoader,
            )
        }

        composable(
            route = LauncherRoute.RECENTS,
            // Rises like the drawer: launcher-owned screens share one motion.
            enterTransition = {
                slideInVertically(
                    animationSpec = tween(DRAWER_ANIMATION_MILLIS),
                    initialOffsetY = { height -> height },
                )
            },
            popExitTransition = {
                slideOutVertically(
                    animationSpec = tween(DRAWER_ANIMATION_MILLIS),
                    targetOffsetY = { height -> height },
                )
            },
        ) {
            RecentAppsRoute(
                onSessionStarted = {
                    navController.popBackStack(LauncherRoute.HOME, inclusive = false)
                },
                onMessage = onMessage,
            )
        }

        composable(
            route = LauncherRoute.APP_DRAWER,
            // Rises from the bottom, the direction the swipe came from, and
            // falls back the same way. One movement, no flourish.
            enterTransition = {
                slideInVertically(
                    animationSpec = tween(DRAWER_ANIMATION_MILLIS),
                    initialOffsetY = { height -> height },
                )
            },
            popExitTransition = {
                slideOutVertically(
                    animationSpec = tween(DRAWER_ANIMATION_MILLIS),
                    targetOffsetY = { height -> height },
                )
            },
        ) {
            AppDrawerRoute(
                // A session starting closes the drawer rather than leaving a
                // grid of apps open over it.
                onSessionStarted = {
                    navController.popBackStack(LauncherRoute.HOME, inclusive = false)
                },
                onMessage = onMessage,
            )
        }
    }
}

/** Short enough to feel immediate, long enough to read as one movement. */
private const val DRAWER_ANIMATION_MILLIS = 220
