package com.resqmesh.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.resqmesh.app.ui.screens.AlertsScreen
import com.resqmesh.app.ui.screens.DashboardScreen
import com.resqmesh.app.ui.screens.HistoryScreen
import com.resqmesh.app.ui.screens.IntroScreen
import com.resqmesh.app.ui.screens.MeshNetworkScreen
import com.resqmesh.app.ui.screens.RescueMapScreen
import com.resqmesh.app.ui.screens.SettingsScreen
import com.resqmesh.app.ui.screens.SosScreen
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

@Composable
fun ResQMeshNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Intro.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // 1. Cinematic 3D Intro Screen
        composable(Screen.Intro.route) {
            IntroScreen(
                onFinishIntro = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Intro.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. Main Futuristic Dashboard
        composable(Screen.Dashboard.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            DashboardScreen(
                viewModel = sharedViewModel,
                onNavigateToSos = { navController.navigate(Screen.SOS.route) },
                onNavigateToMesh = { navController.navigate(Screen.MeshNetwork.route) },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToMap = { navController.navigate(Screen.RescueMap.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        // 3. Dedicated SOS Emergency Screen
        composable(Screen.SOS.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            SosScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 4. Mesh Network Topology Screen
        composable(Screen.MeshNetwork.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            MeshNetworkScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate(Screen.Dashboard.route) },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToMap = { navController.navigate(Screen.RescueMap.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        // 5. Emergency Alerts Feed Screen
        composable(Screen.Alerts.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            AlertsScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate(Screen.Dashboard.route) },
                onNavigateToMesh = { navController.navigate(Screen.MeshNetwork.route) },
                onNavigateToMap = { navController.navigate(Screen.RescueMap.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        // 6. Rescue Map Screen
        composable(Screen.RescueMap.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            RescueMapScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate(Screen.Dashboard.route) },
                onNavigateToMesh = { navController.navigate(Screen.MeshNetwork.route) },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        // 7. Mission History Screen
        composable(Screen.History.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            HistoryScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 8. Settings and Profile Screen
        composable(Screen.Settings.route) {
            val sharedViewModel: ResQMeshViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() },
                onReplayIntro = { navController.navigate(Screen.Intro.route) },
                onNavigateToHome = { navController.navigate(Screen.Dashboard.route) },
                onNavigateToMesh = { navController.navigate(Screen.MeshNetwork.route) },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToMap = { navController.navigate(Screen.RescueMap.route) }
            )
        }
    }
}
