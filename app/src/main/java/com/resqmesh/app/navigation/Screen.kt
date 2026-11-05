package com.resqmesh.app.navigation

sealed class Screen(val route: String) {
    data object Intro : Screen("intro")
    data object Dashboard : Screen("dashboard")
    data object SOS : Screen("sos")
    data object MeshNetwork : Screen("mesh_network")
    data object Alerts : Screen("alerts")
    data object RescueMap : Screen("rescue_map")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
