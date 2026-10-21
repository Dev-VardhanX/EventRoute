package com.eventroute.app.ui.screens.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Event
import com.eventroute.app.ui.components.EmptyState
import com.eventroute.app.ui.components.LoadingState
import com.eventroute.app.ui.theme.ClayBackground

/**
 * Resolves next upcoming registered event journey, or displays an EmptyState if none exist.
 */
@Composable
fun JourneyTabScreen(
    onNavigateToJourney: (String) -> Unit,
    onExploreEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var upcomingEvent by remember { mutableStateOf<Event?>(null) }

    LaunchedEffect(Unit) {
        val cached = AppModule.database.getRegisteredEvents()
        if (cached.isNotEmpty()) {
            upcomingEvent = cached.first()
            onNavigateToJourney(cached.first().id)
        } else {
            AppModule.registrationRepository.getMyRegistrations().fold(
                onSuccess = { events ->
                    if (events.isNotEmpty()) {
                        upcomingEvent = events.first()
                        onNavigateToJourney(events.first().id)
                    } else {
                        loading = false
                    }
                },
                onFailure = {
                    loading = false
                }
            )
        }
    }

    if (loading && upcomingEvent == null) {
        Box(
            modifier = modifier.fillMaxSize().background(ClayBackground),
            contentAlignment = Alignment.Center
        ) {
            LoadingState(message = "Finding upcoming journey...")
        }
    } else if (upcomingEvent == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ClayBackground)
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                title = "No Upcoming Journey",
                message = "Register for an event to plan and view your coordinated journey.",
                icon = Icons.Rounded.Route
            )
            Spacer(Modifier.height(16.dp))
            com.eventroute.app.ui.components.ClayPillButton(
                text = "Explore Events",
                onClick = onExploreEvents
            )
        }
    }
}
