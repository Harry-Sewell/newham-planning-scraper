package com.denmarkarms.scraper.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.denmarkarms.scraper.ui.screens.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Planning : Screen("planning", "Planning", Icons.Default.LocationCity)
    object Documents : Screen("documents", "Documents", Icons.Default.Folder)
    object People : Screen("people", "People", Icons.Default.People)
    object Config : Screen("config", "Config", Icons.Default.Settings)
}

private val bottomNavItems = listOf(Screen.Dashboard, Screen.Planning, Screen.Documents, Screen.People, Screen.Config)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val showBottomBar = currentRoute != "data_manager"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Planning.route) { PlanningTimelineScreen() }
            composable(Screen.Documents.route) { DocumentsScreen() }
            composable(Screen.People.route) { PeopleCompaniesScreen() }
            composable(Screen.Config.route) {
                ConfigScreen(onNavigateToDataManager = { navController.navigate("data_manager") })
            }
            composable("data_manager") {
                DataManagerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
