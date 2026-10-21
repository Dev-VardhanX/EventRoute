package com.eventroute.app.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.eventroute.app.ui.components.ClayBottomNavBar
import com.eventroute.app.ui.navigation.AppNavHost
import com.eventroute.app.ui.navigation.Screen
import com.eventroute.app.ui.navigation.navigateToTab
import com.eventroute.app.ui.theme.ClayBackground

/**
 * Root scaffold providing bottom navigation bar visibility logic and tab back stack control.
 */
@Composable
fun AppScaffold(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabRoutes = setOf(
        Screen.Home.route,
        Screen.MyEvents.route,
        Screen.JourneyTab.route,
        Screen.Profile.route
    )
    val showBar = route in tabRoutes

    val context = androidx.compose.ui.platform.LocalContext.current

    // Tabs back behavior: back on secondary tabs navigates to Home, back on Home exits app
    BackHandler(enabled = route in tabRoutes && route != Screen.Home.route) {
        navController.navigateToTab(Screen.Home.route)
    }

    BackHandler(enabled = route == Screen.Home.route || route == Screen.Onboarding.route) {
        (context as? android.app.Activity)?.finish()
    }

    val selectedTab = when (route) {
        Screen.Home.route -> "home"
        Screen.MyEvents.route -> "my_events"
        Screen.JourneyTab.route -> "journey"
        Screen.Profile.route -> "profile"
        else -> "home"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        )

        if (showBar) {
            ClayBottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { tabKey ->
                    when (tabKey) {
                        "home" -> navController.navigateToTab(Screen.Home.route)
                        "my_events" -> navController.navigateToTab(Screen.MyEvents.route)
                        "journey" -> navController.navigateToTab(Screen.JourneyTab.route)
                        "profile" -> navController.navigateToTab(Screen.Profile.route)
                    }
                },
                onCenterAction = {
                    navController.navigate(Screen.Teams.createRoute("11111111-1111-1111-1111-111111111111"))
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
