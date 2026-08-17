package com.zenmode.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zenmode.app.feature.blockedapps.BlockedAppsRoute
import com.zenmode.app.feature.completion.CompletionRoute
import com.zenmode.app.feature.history.HistoryRoute
import com.zenmode.app.feature.home.HomeRoute
import com.zenmode.app.feature.permissions.OnboardingRoute
import com.zenmode.app.feature.permissions.PermissionsRoute
import com.zenmode.app.feature.settings.SettingsRoute
import com.zenmode.app.feature.statistics.StatisticsRoute
import com.zenmode.app.feature.timer.TimerRoute
import com.zenmode.app.feature.zen.ZenRoute as ZenScreenRoute

/**
 * The app's one navigation graph.
 *
 * The Zen screen is treated specially: entering it clears the back stack, so a
 * running session cannot be casually backed out of into the ordinary screens
 * (specification §31). Stopping is still always available on the screen itself.
 */
@Composable
fun ZenNavHost(
    startDestination: String,
    onCall: () -> Unit,
    onMessage: (String) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(ZenRoute.ONBOARDING) {
            OnboardingRoute(
                onFinished = { navController.replaceWith(ZenRoute.HOME) },
                onMessage = onMessage,
            )
        }

        composable(ZenRoute.HOME) {
            HomeRoute(
                onSessionStarted = { navController.replaceWith(ZenRoute.ZEN) },
                onOpenTimer = { navController.navigate(ZenRoute.TIMER) },
                onOpenStatistics = { navController.navigate(ZenRoute.STATISTICS) },
                onOpenHistory = { navController.navigate(ZenRoute.HISTORY) },
                onOpenSettings = { navController.navigate(ZenRoute.SETTINGS) },
                onOpenPermissions = { navController.navigate(ZenRoute.PERMISSIONS) },
                onMessage = onMessage,
            )
        }

        composable(ZenRoute.TIMER) {
            TimerRoute(
                onBack = { navController.popBackStack() },
                onSessionStarted = { navController.replaceWith(ZenRoute.ZEN) },
                onMessage = onMessage,
            )
        }

        composable(ZenRoute.ZEN) {
            ZenScreenRoute(
                onSessionCompleted = { sessionId ->
                    navController.replaceWith(ZenRoute.completion(sessionId))
                },
                onSessionEnded = { navController.replaceWith(ZenRoute.HOME) },
                onCall = onCall,
            )
        }

        composable(ZenRoute.COMPLETION) {
            CompletionRoute(onDone = { navController.replaceWith(ZenRoute.HOME) })
        }

        composable(ZenRoute.STATISTICS) {
            StatisticsRoute(onBack = { navController.popBackStack() })
        }

        composable(ZenRoute.HISTORY) {
            HistoryRoute(onBack = { navController.popBackStack() })
        }

        composable(ZenRoute.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenBlockedApps = { navController.navigate(ZenRoute.BLOCKED_APPS) },
                onOpenPermissions = { navController.navigate(ZenRoute.PERMISSIONS) },
                onMessage = onMessage,
            )
        }

        composable(ZenRoute.BLOCKED_APPS) {
            BlockedAppsRoute(onBack = { navController.popBackStack() })
        }

        composable(ZenRoute.PERMISSIONS) {
            PermissionsRoute(onBack = { navController.popBackStack() }, onMessage = onMessage)
        }
    }
}

/**
 * Goes to [route] and drops what came before, for the transitions that are not
 * meant to be reversible: starting a session, finishing one, leaving onboarding.
 */
private fun NavHostController.replaceWith(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
