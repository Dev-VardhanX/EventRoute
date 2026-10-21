package com.eventroute.app.ui.screens.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.RouteStep
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.components.map.RouteGoogleMap
import com.eventroute.app.ui.theme.*

private data class RouteOption(val title: String, val highlight: String, val subtitle: String, val ink: Color)

@Composable
fun JourneyScreen(
    eventId: String,
    onBack: () -> Unit,
    onStartLiveJourney: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JourneyViewModel = viewModel()
) {
    LaunchedEffect(eventId) {
        viewModel.loadJourney(eventId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(ClayBackground)) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    ClayTopBar("Route Planner", onBack)
                    LoadingState(message = "Calculating smartest route...")
                }
            }
            is UiState.Empty -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    ClayTopBar("Route Planner", onBack)
                    EmptyState(title = "No route found", message = "Could not find a valid route to this venue.")
                }
            }
            is UiState.Error -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    ClayTopBar("Route Planner", onBack)
                    ErrorState(message = state.message, onRetry = { viewModel.loadJourney(eventId) })
                }
            }
            is UiState.Success -> {
                val data = state.data
                val journey = data.journey
                val selectedRoute = data.selectedRoute ?: journey.routes.firstOrNull()

                val routeOptions = remember(journey.routes) {
                    if (journey.routes.isNotEmpty()) {
                        journey.routes.mapIndexed { idx, r ->
                            val ink = when (idx % 3) {
                                0 -> ClayBlueDeep
                                1 -> ClayGreenText
                                else -> Color(0xFFDB2777)
                            }
                            val durationText = "${r.durationS / 60} min"
                            RouteOption(
                                title = if (idx == 0) "Fastest" else if (idx == 1) "Cheapest" else "Direct",
                                highlight = durationText,
                                subtitle = r.summary,
                                ink = ink
                            )
                        }
                    } else {
                        listOf(
                            RouteOption("Fastest", "45 min", "Metro + Walk", ClayBlueDeep),
                            RouteOption("Cheapest", "₹35", "Bus Only", ClayGreenText),
                            RouteOption("Fewest Transfers", "58 min", "Direct Bus", Color(0xFFDB2777))
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .padding(bottom = 120.dp)
                ) {
                    ClayTopBar("Route Planner", onBack)
                    Spacer(Modifier.height(20.dp))

                    // From / To
                    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp), depth = 12.dp) {
                        Column(Modifier.padding(16.dp)) {
                            LocationRow("FROM", journey.originName, isOrigin = true)
                            Box(Modifier.padding(start = 21.dp).width(2.dp).height(10.dp).background(Color(0xFFD9E2EF)))
                            LocationRow("TO", journey.destinationName, isOrigin = false)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Leave-by banner
                    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(34.dp), backgroundColor = ClayGreenPrimary, depth = 12.dp) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TO ARRIVE BY ${journey.arriveAt}", style = ClayType.Label.copy(fontSize = 10.sp, color = ClayGreenText))
                            Spacer(Modifier.height(6.dp))
                            Text(journey.leaveBy, style = ClayType.Display.copy(fontSize = 46.sp))
                            Spacer(Modifier.height(4.dp))
                            val bannerLabel = if (journey.isLeaveNow) "Leave now recommended" else "Recommended leave time"
                            Text(bannerLabel, style = ClayType.BodyStrong.copy(fontSize = 13.sp, color = ClayGreenText))
                        }
                    }

                    // Buffer breakdown if present
                    if (journey.bufferBreakdown.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), depth = 8.dp) {
                            Column(Modifier.padding(16.dp)) {
                                Text("How we calculated this", style = ClayType.BodyStrong.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                Spacer(Modifier.height(8.dp))
                                journey.bufferBreakdown.forEach { item ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.label, style = ClayType.Caption.copy(fontSize = 12.sp, color = ClayTextSecondary))
                                        Text("+${item.minutes} min", style = ClayType.Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ClayBlueDeep))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Real Google Map for selected route
                    if (selectedRoute != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clay(RoundedCornerShape(32.dp), ClayInsetBg, depth = 10.dp)
                                .clip(RoundedCornerShape(32.dp))
                        ) {
                            RouteGoogleMap(
                                route = selectedRoute,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    Text("Route Options", style = ClayType.Section, modifier = Modifier.padding(start = 4.dp))

                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        routeOptions.forEachIndexed { i, o ->
                            val on = i == data.selectedRouteIndex
                            val shape = RoundedCornerShape(24.dp)
                            Column(
                                Modifier
                                    .clayPress({ viewModel.selectRoute(i) }, true, remember { MutableInteractionSource() })
                                    .clay(shape, if (on) Color(0xFFF1F8FF) else Color.White, depth = 9.dp)
                                    .then(if (on) Modifier.border(2.dp, ClayBlueDeep.copy(alpha = 0.7f), shape) else Modifier)
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(o.title, style = ClayType.BodyStrong.copy(fontSize = 12.sp, color = o.ink))
                                Text(o.highlight, style = ClayType.Heading.copy(fontSize = 21.sp))
                                Text(o.subtitle, style = ClayType.Caption.copy(fontSize = 11.sp, color = ClayTextMuted))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val stepsToDisplay = selectedRoute?.legs?.flatMap { it.steps } ?: journey.steps

                    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp), depth = 12.dp) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                            Text("Route Breakdown", style = ClayType.Section)
                            Spacer(Modifier.height(16.dp))
                            stepsToDisplay.forEachIndexed { i, step ->
                                RouteStepRow(step, isLast = i == stepsToDisplay.lastIndex)
                            }
                        }
                    }
                }

                ClayStickyBar(Modifier.align(Alignment.BottomCenter)) {
                    ClayPillButton("Start Live Journey", onStartLiveJourney, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LocationRow(label: String, value: String, isOrigin: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClayIconBadgeDot(isOrigin)
        Spacer(Modifier.width(12.dp))
        ClayWell(Modifier.weight(1f), RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(label, style = ClayType.Label.copy(fontSize = 9.sp))
                Text(value, style = ClayType.BodyStrong.copy(fontSize = 14.sp))
            }
        }
    }
}

@Composable
private fun ClayIconBadgeDot(isOrigin: Boolean) {
    if (isOrigin) {
        Box(Modifier.size(44.dp).clay(RoundedCornerShape(16.dp), ClayBluePrimary, depth = 7.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(ClayBlueDeep, CircleShape))
        }
    } else {
        ClayIconBadge(Icons.Rounded.LocationOn, background = ClayPinkTag, tint = Color(0xFFDB2777), size = 44.dp, shape = RoundedCornerShape(16.dp))
    }
}

private fun modeIcon(mode: String, isLast: Boolean): ImageVector = when {
    isLast -> Icons.Rounded.LocationOn
    mode == "walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    mode == "metro" -> Icons.Rounded.DirectionsSubway
    mode == "bus" -> Icons.Rounded.DirectionsBus
    mode == "transfer" -> Icons.Rounded.SwapHoriz
    else -> Icons.Rounded.LocationOn
}

@Composable
private fun RouteStepRow(step: RouteStep, isLast: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ClayIconBadge(
                modeIcon(step.mode, isLast),
                background = if (isLast) ClayPinkTag else ClayBluePrimary,
                tint = if (isLast) Color(0xFFDB2777) else ClayBlueDeep,
                size = 38.dp
            )
            if (!isLast) Box(Modifier.width(3.dp).height(26.dp).background(Color(0xFFDDE6F2), RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.padding(top = 2.dp, bottom = if (isLast) 0.dp else 14.dp)) {
            Text(step.instruction, style = ClayType.BodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            val meta = buildString {
                if (step.durationMinutes > 0) append("${step.durationMinutes} min")
                if (step.distanceMeters > 0) append(" • ${step.distanceMeters}m")
            }
            if (meta.isNotEmpty() && !isLast) Text(meta, style = ClayType.Caption.copy(fontSize = 11.sp))
        }
    }
}
