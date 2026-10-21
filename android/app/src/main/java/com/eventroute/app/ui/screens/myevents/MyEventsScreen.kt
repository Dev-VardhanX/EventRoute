package com.eventroute.app.ui.screens.myevents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.eventroute.app.R
import com.eventroute.app.domain.model.Event
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

private data class TicketData(
    val eventId: String,
    val passLabel: String,
    val title: String,
    val date: String,
    val time: String,
    val venue: String,
    val code: String,
    val token: String,
    val startMillis: Long = System.currentTimeMillis() + 86400000L,
    val endMillis: Long = System.currentTimeMillis() + 90000000L
)

@Composable
fun MyEventsScreen(
    onEventClick: (String) -> Unit,
    onTrackRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MyEventsViewModel = viewModel()
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ticket by remember { mutableStateOf<TicketData?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedUiState by viewModel.savedUiState.collectAsStateWithLifecycle()

    BackHandler(enabled = ticket != null) {
        ticket = null
    }

    val open = ticket
    if (open != null) {
        TicketView(open, onClose = { ticket = null })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("My Events", style = ClayType.Title, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(16.dp))
        ClaySegmented(listOf("Upcoming", "Saved", "Past"), tab, { tab = it })
        Spacer(Modifier.height(20.dp))

        when (tab) {
            0 -> {
                when (val state = uiState) {
                    is UiState.Loading -> {
                        LoadingState(message = "Loading registered events...")
                    }
                    is UiState.Empty -> {
                        EmptyState(Icons.Rounded.Event, "No upcoming events yet", "Register for an event to see tickets and leave-by plans here.")
                    }
                    is UiState.Error -> {
                        ErrorState(message = state.message, onRetry = { viewModel.loadEvents() })
                    }
                    is UiState.Success -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 120.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.data) { event ->
                                val isTeam = event.participationType == "team"
                                val isHack = event.title.contains("Hack", ignoreCase = true)
                                val month = if (isHack) "OCT" else "NOV"
                                val day = if (isHack) "24" else "02"
                                val ticketData = TicketData(
                                    eventId = event.id,
                                    passLabel = if (isTeam) "TEAM PASS" else "SOLO PASS",
                                    title = event.title,
                                    date = "$day $month, 2024",
                                    time = event.formattedTime.ifEmpty { "10:00 AM IST" },
                                    venue = event.venueAddress.ifEmpty { event.venueName },
                                    code = if (event.ticketCode.isNotEmpty()) event.ticketCode else "#EVRT-${event.id.takeLast(4).uppercase()}-X90",
                                    token = if (event.ticketToken.isNotEmpty()) event.ticketToken else "evrt.${event.id}.u-john.7e8b2a"
                                )
                                MyEventClayCard(
                                    month = month,
                                    day = day,
                                    badgeBg = if (isHack) ClayBlueLight else ClayPinkLight,
                                    badgeInk = if (isHack) ClayBlueDeep else ClayPinkButton,
                                    title = event.title,
                                    subtitle = "${event.collegeName} • ${if (isTeam) "Team Journey" else "Solo Entry"}",
                                    alertLabel = if (isHack) "NEXT ACTION" else "TRAVEL UPDATE",
                                    alertText = if (isHack) {
                                        buildAnnotatedString {
                                            append("Leave by ")
                                            withStyle(SpanStyle(color = ClayGreenText, fontWeight = FontWeight.Bold)) { append(event.smartLeaveBy ?: "8:15 AM") }
                                            append(" tomorrow")
                                        }
                                    } else {
                                        AnnotatedString("Route available in 5 days")
                                    },
                                    alertIcon = if (isHack) Icons.Rounded.Notifications else Icons.Rounded.Schedule,
                                    alertTint = if (isHack) ClayGreenLight else ClaySurfaceSecondary,
                                    alertTile = if (isHack) ClayGreenBright else ClayGrayChip,
                                    alertTileIcon = if (isHack) Color.White else ClayTextSecondary,
                                    alertInk = if (isHack) ClayGreenText else ClayTextSecondary,
                                    routeEnabled = isHack,
                                    onViewTicket = { ticket = ticketData },
                                    onTrackRoute = { onTrackRoute(event.id) }
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                when (val state = savedUiState) {
                    is UiState.Loading -> LoadingState(message = "Loading saved events...")
                    is UiState.Empty -> EmptyState(Icons.Rounded.BookmarkBorder, "No saved events yet", "Tap the bookmark on any event to keep it here.")
                    is UiState.Error -> ErrorState(message = state.message, onRetry = { viewModel.loadSavedEvents() })
                    is UiState.Success -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 120.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.data) { event ->
                                val banner = if (event.id.contains("2222") || event.title.contains("Robo", ignoreCase = true)) {
                                    R.drawable.robocup_banner
                                } else {
                                    R.drawable.hackx_banner
                                }
                                EventClayCard(
                                    event = event,
                                    onViewDetails = { onEventClick(event.id) },
                                    bannerDrawableId = banner
                                )
                            }
                        }
                    }
                }
            }
            else -> EmptyState(Icons.Rounded.Event, "No past events yet", "Events you have attended will show up here.")
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            ClayIconBadge(icon, background = ClayBluePrimary, tint = ClayBlueText, size = 84.dp, shape = RoundedCornerShape(28.dp))
            Spacer(Modifier.height(18.dp))
            Text(title, style = ClayType.Heading)
            Spacer(Modifier.height(6.dp))
            Text(body, style = ClayType.Body.copy(textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun MyEventClayCard(
    month: String, day: String,
    badgeBg: Color, badgeInk: Color,
    title: String, subtitle: String,
    alertLabel: String, alertText: AnnotatedString,
    alertIcon: ImageVector,
    alertTint: Color, alertTile: Color, alertTileIcon: Color, alertInk: Color,
    routeEnabled: Boolean,
    onViewTicket: () -> Unit,
    onTrackRoute: () -> Unit,
) {
    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp), depth = 12.dp) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 26.dp, y = (-26).dp).size(86.dp)
                    .background(badgeBg.copy(alpha = 0.7f), CircleShape)
            )
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.size(60.dp).clay(RoundedCornerShape(22.dp), badgeBg, depth = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(month, style = ClayType.Label.copy(fontSize = 10.sp, color = badgeInk))
                        Text(day, style = ClayType.Heading.copy(fontSize = 22.sp, color = if (badgeInk == ClayPinkButton) ClayPinkText else ClayBlueText))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(title, style = ClayType.CardTitle)
                        Text(subtitle, style = ClayType.Body.copy(fontSize = 12.sp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                ClayWell(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), alertTint) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClayIconBadge(alertIcon, background = alertTile, tint = alertTileIcon, size = 38.dp, shape = RoundedCornerShape(13.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(alertLabel, style = ClayType.Label.copy(fontSize = 9.sp, color = alertInk))
                            Text(alertText, style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ClaySmallButton("View Ticket", onViewTicket, Modifier.weight(1f))
                    ClaySmallButton(
                        "Track Route", onTrackRoute, Modifier.weight(1f),
                        backgroundColor = ClayBluePrimary, textColor = ClayBlueText, enabled = routeEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketView(t: TicketData, onClose: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ClayTopBar(
            title = "Event Ticket",
            onBack = onClose,
            trailing = {
                ClayIconButton(
                    icon = Icons.Rounded.Share,
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, t.title)
                            putExtra(Intent.EXTRA_TEXT, "${t.title} - https://eventroute.app/event/${t.eventId}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Event"))
                    },
                    contentDescription = "Share event"
                )
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 140.dp)
        ) {
            ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(36.dp), depth = 14.dp) {
                Column(Modifier.padding(24.dp)) {
                    ClayTag(t.passLabel, ClayAmberLight, ClayAmberText)
                    Spacer(Modifier.height(12.dp))
                    Text(t.title, style = ClayType.Title.copy(fontSize = 28.sp))
                    Text("Delhi-NCR College Circuit", style = ClayType.Caption)

                    Spacer(Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TicketField("DATE", t.date)
                        TicketField("TIME", t.time, Alignment.End)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TicketField("VENUE", t.venue)
                        TicketField("SEAT", "General Access", Alignment.End)
                    }

                    Spacer(Modifier.height(26.dp))
                    DottedDivider()
                    Spacer(Modifier.height(24.dp))

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val qrSize = minOf(230.dp, maxWidth * 0.6f)
                            ClayQrCode(content = t.token, size = qrSize)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(t.code, style = ClayType.Mono.copy(letterSpacing = 2.sp))
                        Spacer(Modifier.height(4.dp))
                        Text("SCAN AT ENTRY GATE", style = ClayType.Label.copy(fontSize = 10.sp, color = ClayTextMuted))
                        Spacer(Modifier.height(18.dp))
                        ClaySmallButton(
                            "Add to Calendar",
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, t.title)
                                    putExtra(CalendarContract.Events.EVENT_LOCATION, t.venue)
                                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, t.startMillis)
                                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, t.endMillis)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ClayBluePrimary,
                            textColor = ClayBlueText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketField(label: String, value: String, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Text(label, style = ClayType.Label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = ClayType.BodyStrong.copy(fontSize = 14.sp))
    }
}

@Composable
private fun DottedDivider() {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )
    }
}
