package com.eventroute.app.ui.navigation

import androidx.navigation.NavHostController

fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(Screen.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

fun NavHostController.navigateClearingTo(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
