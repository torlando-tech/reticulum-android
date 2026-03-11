package tech.torlando.rns.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tech.torlando.rns.ui.screens.DiscoveredInterfacesScreen
import tech.torlando.rns.ui.screens.HomeScreen
import tech.torlando.rns.ui.screens.InterfacesScreen
import tech.torlando.rns.ui.screens.MonitorScreen
import tech.torlando.rns.ui.screens.SettingsScreen
import tech.torlando.rns.viewmodel.TransportViewModel

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Interfaces("interfaces", "Interfaces", Icons.AutoMirrored.Filled.List),
    Monitor("monitor", "Monitor", Icons.Default.Monitor),
    Settings("settings", "Settings", Icons.Default.Settings),
}

@Composable
fun AppNavigation(viewModel: TransportViewModel = viewModel()) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) { HomeScreen(viewModel) }
            composable(Screen.Interfaces.route) {
                InterfacesScreen(
                    viewModel = viewModel,
                    onNavigateToDiscovery = { navController.navigate("discovered_interfaces") },
                )
            }
            composable(Screen.Monitor.route) { MonitorScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
            composable("discovered_interfaces") {
                DiscoveredInterfacesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
