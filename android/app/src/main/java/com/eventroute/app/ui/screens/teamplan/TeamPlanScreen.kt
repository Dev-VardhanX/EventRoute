package com.eventroute.app.ui.screens.teamplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.TeamMember
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

@Composable
fun TeamPlanScreen(
    teamId: String,
    onBack: () -> Unit,
    onOpenStartPoint: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeamPlanViewModel = viewModel()
) {
    LaunchedEffect(teamId) {
        viewModel.loadPlan(teamId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        ClayTopBar("Team Travel Plan", onBack)
        Spacer(Modifier.height(18.dp))

        when (val state = uiState) {
            is UiState.Loading -> {
                LoadingState(message = "Loading team plan...")
            }
            is UiState.Empty -> {
                EmptyState(title = "No plan available", message = "Team travel plan is not yet configured.")
            }
            is UiState.Error -> {
                ErrorState(message = state.message, onRetry = { viewModel.loadPlan(teamId) })
            }
            is UiState.Success -> {
                val plan = state.data

                ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp), backgroundColor = ClayBluePrimary, depth = 12.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TARGET ARRIVAL • V${plan.planVersion}", style = ClayType.Label.copy(color = ClayBlueDeep))
                            Text(plan.targetArrivalAt.ifEmpty { "09:30 AM" }, style = ClayType.Title.copy(fontSize = 30.sp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (plan.isAtRisk) {
                                ClayTag("AT RISK", ClayAlertPink, ClayPinkText)
                            } else {
                                ClayTag("${plan.arrivalSpreadMinutes} MIN SPREAD", ClayGreenPrimary, ClayGreenText)
                            }
                            Spacer(Modifier.height(6.dp))
                            val spreadSub = if (plan.isAtRisk) plan.atRiskReason ?: "High spread risk" else "ALL TOGETHER"
                            Text(spreadSub, style = ClayType.Label.copy(fontSize = 9.sp, color = ClayBlueDeep))
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Teammate Schedule", style = ClayType.Section)
                    Text(
                        "Change Start",
                        style = ClayType.BodyStrong.copy(fontSize = 13.sp, color = ClayBlueDeep),
                        modifier = Modifier.clayPress({ onOpenStartPoint(teamId) }, true, remember { MutableInteractionSource() })
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(plan.members) { TeammateScheduleCard(it) }

                    if (plan.suggestions.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Join on the way?", style = ClayType.Section)
                                Icon(Icons.Rounded.Info, "Why am I seeing this?", tint = ClayTextMuted, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        items(plan.suggestions) { suggestion ->
                            val accepted = suggestion.status.equals("accepted", ignoreCase = true)
                            val declined = suggestion.status.equals("declined", ignoreCase = true)

                            ClayCard(
                                Modifier.fillMaxWidth(),
                                RoundedCornerShape(32.dp),
                                backgroundColor = if (accepted) ClayGreenLight else if (declined) ClaySurfaceSecondary else ClayAmberLight,
                                depth = 11.dp
                            ) {
                                Column(Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ClayIconBadge(
                                            Icons.Rounded.Handshake,
                                            background = if (accepted) ClayGreenPrimary else if (declined) Color(0xFFCBD5E1) else Color(0xFFFFF4C2),
                                            tint = if (accepted) ClayGreenText else if (declined) ClayTextSecondary else ClayAmberText,
                                            size = 48.dp,
                                            shape = RoundedCornerShape(17.dp)
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                suggestion.summary.ifEmpty { "Shared Travel Suggestion" },
                                                style = ClayType.BodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ClayAmberInk)
                                            )
                                            val statusText = when {
                                                accepted -> "Accepted • Meet point confirmed"
                                                declined -> "Declined • Traveling individually"
                                                else -> "You and Sarah arrive within 2 mins (Cab split: Est. ₹45 each)"
                                            }
                                            Text(statusText, style = ClayType.Caption.copy(fontSize = 12.sp, color = ClayAmberText))
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        ClaySmallButton(
                                            if (declined) "Declined" else "Decline",
                                            { viewModel.respondSuggestion(teamId, suggestion.id, false) },
                                            Modifier.weight(1f),
                                            backgroundColor = if (declined) ClayAlertPink else Color.White,
                                            textColor = if (declined) ClayPinkText else ClayAmberInk,
                                            enabled = !declined
                                        )
                                        ClaySmallButton(
                                            if (accepted) "Accepted" else "Accept Meetup",
                                            { viewModel.respondSuggestion(teamId, suggestion.id, true) },
                                            Modifier.weight(1f),
                                            backgroundColor = if (accepted) ClayGreenPrimary else ClayAmberButton,
                                            textColor = if (accepted) ClayGreenText else ClayAmberInk,
                                            enabled = !accepted
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeammateScheduleCard(member: TeamMember) {
    val shape = RoundedCornerShape(28.dp)
    ClayCard(
        Modifier
            .fillMaxWidth()
            .then(if (member.isLeader) Modifier.border(2.dp, ClayBlueDeep.copy(alpha = 0.75f), shape) else Modifier),
        shape, depth = 10.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayAvatar(member.name, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (member.isLeader) "You (${member.startAddress.ifEmpty { "Dwarka" }})" else "${member.name.split(" ")[0]} (${member.startAddress.ifEmpty { "Rohini" }})",
                    style = ClayType.BodyStrong.copy(fontSize = 15.sp)
                )
                Text(member.travelPref, style = ClayType.Caption)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("LEAVE BY", style = ClayType.Label.copy(fontSize = 9.sp))
                Text(
                    member.plannedLeaveBy ?: "08:15 AM",
                    style = ClayType.Heading.copy(fontSize = 19.sp, color = if (member.isLeader) ClayBlueDeep else ClayTextPrimary)
                )
            }
        }
    }
}
