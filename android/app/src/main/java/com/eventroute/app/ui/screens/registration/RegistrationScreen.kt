package com.eventroute.app.ui.screens.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.Event
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

@Composable
fun RegistrationScreen(
    eventId: String,
    onRegistrationComplete: () -> Unit,
    onNavigateToTeams: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegistrationViewModel = viewModel()
) {
    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isConfirmed by remember { mutableStateOf(false) }

    when (val state = uiState.eventState) {
        is UiState.Loading -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                ClayTopBar("Event Registration", onBack)
                Spacer(Modifier.height(24.dp))
                LoadingState(message = "Loading registration details...")
            }
        }
        is UiState.Empty -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                ClayTopBar("Event Registration", onBack)
                Spacer(Modifier.height(24.dp))
                EmptyState(title = "Event not found", message = "Could not find details for this event.")
            }
        }
        is UiState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                ClayTopBar("Event Registration", onBack)
                Spacer(Modifier.height(24.dp))
                ErrorState(message = state.message, onRetry = { viewModel.loadEvent(eventId) })
            }
        }
        is UiState.Success -> {
            val event = state.data
            val isTeam = event.participationType == "team"

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    ClayTopBar("Event Registration", onBack)
                    Spacer(Modifier.height(22.dp))

                    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp), depth = 12.dp) {
                        Column(Modifier.padding(22.dp)) {
                            ClayTag(
                                if (isTeam) "TEAM REGISTRATION" else "SOLO PASS",
                                if (isTeam) ClayPinkTag else ClayBlueLight,
                                if (isTeam) ClayPinkText else ClayBlueInk
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(event.title, style = ClayType.Title.copy(fontSize = 24.sp))
                            Text("${event.collegeName} • ${event.venueName}", style = ClayType.Body.copy(fontSize = 13.sp))
                            Spacer(Modifier.height(18.dp))

                            ClayWell(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("STUDENT SUMMARY", style = ClayType.Label)
                                    SummaryRow(Icons.Rounded.Person, "Attendee", "John Doe (IIT Delhi)")
                                    SummaryRow(
                                        Icons.Rounded.Payments, "Registration fee",
                                        if (event.feeAmount == 0.0) "Free" else "₹${event.feeAmount.toInt()}"
                                    )
                                    SummaryRow(Icons.Rounded.Schedule, "Check-in time", event.formattedTime.ifEmpty { "10:00 AM IST" })
                                }
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (isTeam || event.participationType == "either") {
                        ClayPillButton(
                            "Create or Join Team Lobby", { onNavigateToTeams(event.id) }, Modifier.fillMaxWidth(),
                            backgroundColor = Color.White, textColor = ClayBlueDeep, icon = Icons.Rounded.Groups
                        )
                    }
                    ClayPillButton(
                        text = if (isConfirmed) "Registration Confirmed" else "Confirm Registration",
                        onClick = {
                            if (!isConfirmed) {
                                viewModel.registerSolo(event.id) {
                                    isConfirmed = true
                                    onRegistrationComplete()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (isConfirmed) ClayGreenPrimary else ClayBluePrimary,
                        textColor = if (isConfirmed) ClayGreenInk else ClayBlueText,
                        icon = if (isConfirmed) Icons.Rounded.Check else null
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ClayBlueDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = ClayType.Body.copy(fontSize = 13.sp))
        }
        Text(value, style = ClayType.BodyStrong.copy(fontSize = 13.sp, color = ClayTextPrimary))
    }
}
