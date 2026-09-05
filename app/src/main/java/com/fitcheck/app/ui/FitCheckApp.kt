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
import com.fitcheck.app.ui.screens.dressme.DressMeScreen
import com.fitcheck.app.ui.screens.wardrobe.WardrobeScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_STYLE = "style"
private const val ROUTE_AI_LAB = "ai_lab"
private const val ROUTE_WARDROBE = "wardrobe"
private const val ROUTE_DRESS_ME = "dress_me"

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
                    selected = currentRoute == ROUTE_DRESS_ME,
                    onClick = { navController.navigate(ROUTE_DRESS_ME) { launchSingleTop = true } },
                    icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                    label = { Text("Dress Me") }
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_STYLE,
                    onClick = { navController.navigate(ROUTE_WARDROBE) { launchSingleTop = true } },
                    icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                    label = { Text("Wardrobe") }
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
            startDestination = ROUTE_DRESS_ME,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_HOME) { DressMeScreen() }
            composable(ROUTE_DRESS_ME) { DressMeScreen() }
            composable(ROUTE_WARDROBE) { WardrobeScreen() }
            composable(ROUTE_STYLE) { WardrobeScreen() }
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
