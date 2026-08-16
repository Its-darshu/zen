package com.zenmode.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ZenNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = ZenRoute.HOME,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(ZenRoute.HOME) { PlaceholderScreen("Home") }
        composable(ZenRoute.TIMER) { PlaceholderScreen("Timer") }
        composable(ZenRoute.ZEN) { PlaceholderScreen("Zen") }
        composable(ZenRoute.STATISTICS) { PlaceholderScreen("Statistics") }
        composable(ZenRoute.HISTORY) { PlaceholderScreen("History") }
        composable(ZenRoute.SETTINGS) { PlaceholderScreen("Settings") }
        composable(ZenRoute.BLOCKED_APPS) { PlaceholderScreen("Blocked apps") }
        composable(ZenRoute.PERMISSIONS) { PlaceholderScreen("Permissions") }
    }
}

/** Temporary destination body, replaced feature by feature. */
@Composable
private fun PlaceholderScreen(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
