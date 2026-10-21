package com.eventroute.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.eventroute.app.ui.screens.auth.AuthScreen
import com.eventroute.app.ui.screens.eventdetails.EventDetailsScreen
import com.eventroute.app.ui.screens.home.HomeScreen
import com.eventroute.app.ui.screens.home.HomeViewModel
import com.eventroute.app.ui.screens.journey.JourneyScreen
import com.eventroute.app.ui.screens.journey.JourneyTabScreen
import com.eventroute.app.ui.screens.livejourney.LiveJourneyScreen
import com.eventroute.app.ui.screens.map.MapExploreScreen
import com.eventroute.app.ui.screens.myevents.MyEventsScreen
import com.eventroute.app.ui.screens.notifications.NotificationsScreen
import com.eventroute.app.ui.screens.onboarding.OnboardingScreen
import com.eventroute.app.ui.screens.profile.ProfileScreen
import com.eventroute.app.ui.screens.registration.RegistrationScreen
import com.eventroute.app.ui.screens.teamplan.TeamPlanScreen
import com.eventroute.app.ui.screens.teams.TeamsScreen
import com.eventroute.app.ui.screens.venuemap.VenueMapScreen

/**
 * Single source of truth for all application routes and deep link configurations.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) },
    ) {
        // ── Auth Graph ──
        navigation(
            startDestination = Screen.Onboarding.route,
            route = Screen.AuthGraph.route,
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onGetStarted = { navController.navigate(Screen.Auth.route) },
                    onLoginClick = { navController.navigate(Screen.Auth.route) },
                )
            }

            composable(Screen.Auth.route) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate(Screen.MainGraph.route) {
                            popUpTo(Screen.AuthGraph.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Main Graph ──
        navigation(
            startDestination = Screen.Home.route,
            route = Screen.MainGraph.route,
        ) {
            // Tab destinations
            composable(Screen.Home.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.MainGraph.route)
                }
                val homeViewModel: HomeViewModel = viewModel(parentEntry)
                HomeScreen(
                    viewModel = homeViewModel,
                    onEventClick = { id ->
                        navController.navigate(Screen.EventDetails.createRoute(id))
                    },
                    onOpenMapExplore = {
                        navController.navigate(Screen.MapExplore.route)
                    },
                )
            }

            composable(Screen.MyEvents.route) {
                MyEventsScreen(
                    onEventClick = { id ->
                        navController.navigate(Screen.EventDetails.createRoute(id))
                    },
                    onTrackRoute = { id ->
                        navController.navigate(Screen.Journey.createRoute(id))
                    },
                )
            }

            composable(Screen.JourneyTab.route) {
                JourneyTabScreen(
                    onNavigateToJourney = { eventId ->
                        navController.navigate(Screen.Journey.createRoute(eventId)) {
                            popUpTo(Screen.JourneyTab.route) { inclusive = true }
                        }
                    },
                    onExploreEvents = {
                        navController.navigateToTab(Screen.Home.route)
                    },
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigateClearingTo(Screen.AuthGraph.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.NotificationSettings.route)
                    },
                )
            }

            // Map Explore (draws its own bottom bar; host bar hidden)
            composable(Screen.MapExplore.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.MainGraph.route)
                }
                val homeViewModel: HomeViewModel = viewModel(parentEntry)
                MapExploreScreen(
                    viewModel = homeViewModel,
                    onTabSelected = { key ->
                        when (key) {
                            "home" -> navController.navigateToTab(Screen.Home.route)
                            "journey" -> navController.navigateToTab(Screen.JourneyTab.route)
                            "profile" -> navController.navigateToTab(Screen.Profile.route)
                            "map" -> {}
                            else -> navController.navigateToTab(key)
                        }
                    },
                    onCenterAction = {
                        navController.navigate(Screen.Teams.createRoute("11111111-1111-1111-1111-111111111111"))
                    },
                    onEventClick = { id ->
                        navController.navigate(Screen.EventDetails.createRoute(id))
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigateToTab(Screen.Home.route)
                        }
                    },
                )
            }

            // Event Details with deep links
            composable(
                route = Screen.EventDetails.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "eventroute://event/{eventId}" },
                    navDeepLink { uriPattern = "https://eventroute.app/event/{eventId}" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("eventId").orEmpty()
                EventDetailsScreen(
                    eventId = id,
                    onBack = { navController.popBackStack() },
                    onRegisterClick = { navController.navigate(Screen.Registration.createRoute(it)) },
                    onPlanJourneyClick = { navController.navigate(Screen.Journey.createRoute(it)) },
                    onOpenVenueMap = { venueEventId ->
                        navController.navigate(Screen.VenueMap.createRoute(venueEventId))
                    },
                )
            }

            // Venue Map (in-app expandable venue map)
            composable(
                route = Screen.VenueMap.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) },
            ) { entry ->
                val id = entry.arguments?.getString("eventId").orEmpty()
                VenueMapScreen(
                    eventId = id,
                    onPlanRoute = { routeEventId ->
                        navController.navigate(Screen.Journey.createRoute(routeEventId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Registration
            composable(
                route = Screen.Registration.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("eventId").orEmpty()
                RegistrationScreen(
                    eventId = id,
                    onRegistrationComplete = {
                        navController.navigate(Screen.MyEvents.route) {
                            popUpTo(Screen.Home.route)
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTeams = { teamEventId ->
                        navController.navigate(Screen.Teams.createRoute(teamEventId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Teams lobby
            composable(
                route = Screen.Teams.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("eventId").orEmpty()
                TeamsScreen(
                    eventId = id,
                    onViewTeamPlan = { teamId ->
                        navController.navigate(Screen.TeamPlan.createRoute(teamId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Invite deep link: opens join-team flow
            composable(
                route = Screen.Invite.route,
                arguments = listOf(navArgument("code") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "eventroute://invite/{code}" },
                    navDeepLink { uriPattern = "https://eventroute.app/invite/{code}" },
                ),
            ) { entry ->
                val code = entry.arguments?.getString("code").orEmpty()
                TeamsScreen(
                    eventId = "11111111-1111-1111-1111-111111111111",
                    inviteCode = code,
                    onViewTeamPlan = { teamId ->
                        navController.navigate(Screen.TeamPlan.createRoute(teamId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Team Plan
            composable(
                route = Screen.TeamPlan.route,
                arguments = listOf(navArgument("teamId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "eventroute://team/{teamId}/plan" },
                    navDeepLink { uriPattern = "https://eventroute.app/team/{teamId}/plan" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("teamId").orEmpty()
                TeamPlanScreen(
                    teamId = id,
                    onBack = { navController.popBackStack() },
                    onOpenStartPoint = { teamId ->
                        navController.navigate(Screen.TeamStartPoint.createRoute(teamId))
                    },
                )
            }

            // Team Start Point
            composable(
                route = Screen.TeamStartPoint.route,
                arguments = listOf(navArgument("teamId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("teamId").orEmpty()
                com.eventroute.app.ui.screens.startpoint.TeamStartPointScreen(
                    teamId = id,
                    onBack = { navController.popBackStack() },
                    onPlanUpdated = { navController.popBackStack() },
                )
            }

            // Journey Planner
            composable(
                route = Screen.Journey.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "eventroute://journey/{eventId}" },
                    navDeepLink { uriPattern = "https://eventroute.app/journey/{eventId}" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("eventId").orEmpty()
                JourneyScreen(
                    eventId = id,
                    onBack = { navController.popBackStack() },
                    onStartLiveJourney = {
                        navController.navigate(Screen.LiveJourney.createRoute(id))
                    },
                )
            }

            // Live Journey tracking
            composable(
                route = Screen.LiveJourney.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("eventId").orEmpty()
                LiveJourneyScreen(
                    eventId = id,
                    onBack = { navController.popBackStack() },
                )
            }

            // Notifications
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // App Settings / Notification Settings
            composable(Screen.NotificationSettings.route) {
                com.eventroute.app.ui.screens.settings.NotificationSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
