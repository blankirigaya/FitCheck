package com.fitcheck.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitcheck.app.ui.screens.ailab.AiLabScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_STYLE = "style"
private const val ROUTE_AI_LAB = "ai_lab"

@Composable
fun FitCheckApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == ROUTE_HOME,
                    onClick = { navController.navigate(ROUTE_HOME) { launchSingleTop = true } },
                    icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_STYLE,
                    onClick = { navController.navigate(ROUTE_STYLE) { launchSingleTop = true } },
                    icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                    label = { Text("Style") }
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_AI_LAB,
                    onClick = { navController.navigate(ROUTE_AI_LAB) { launchSingleTop = true } },
                    icon = { Icon(Icons.Outlined.Science, contentDescription = null) },
                    label = { Text("AI Lab") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_AI_LAB,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_HOME) { PlaceholderScreen(title = "Home", subtitle = "Phase 1 scaffold") }
            composable(ROUTE_STYLE) { PlaceholderScreen(title = "Style", subtitle = "Coming next") }
            composable(ROUTE_AI_LAB) { AiLabScreen() }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
