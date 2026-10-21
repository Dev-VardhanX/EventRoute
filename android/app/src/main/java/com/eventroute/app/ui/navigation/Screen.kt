package com.eventroute.app.ui.navigation

sealed class Screen(val route: String) {
    object AuthGraph : Screen("auth_graph")
    object MainGraph : Screen("main_graph")
    object Onboarding : Screen("onboarding")
    object Auth : Screen("auth")
    object Home : Screen("home")
    object MapExplore : Screen("map_explore")
    object EventDetails : Screen("event_details/{eventId}") {
        fun createRoute(eventId: String) = "event_details/$eventId"
    }
    object Registration : Screen("registration/{eventId}") {
        fun createRoute(eventId: String) = "registration/$eventId"
    }
    object Teams : Screen("teams/{eventId}") {
        fun createRoute(eventId: String) = "teams/$eventId"
    }
    object Invite : Screen("invite/{code}") {
        fun createRoute(code: String) = "invite/$code"
    }
    object MyEvents : Screen("my_events")
    object JourneyTab : Screen("journey_tab")
    object Journey : Screen("journey/{eventId}") {
        fun createRoute(eventId: String) = "journey/$eventId"
    }
    object LiveJourney : Screen("live_journey/{eventId}") {
        fun createRoute(eventId: String) = "live_journey/$eventId"
    }
    object TeamPlan : Screen("team_plan/{teamId}") {
        fun createRoute(teamId: String) = "team_plan/$teamId"
    }
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object NotificationSettings : Screen("settings/notifications")
    object TeamStartPoint : Screen("team_start_point/{teamId}") {
        fun createRoute(teamId: String) = "team_start_point/$teamId"
    }
    object VenueMap : Screen("venue_map/{eventId}") {
        fun createRoute(eventId: String) = "venue_map/$eventId"
    }
}
