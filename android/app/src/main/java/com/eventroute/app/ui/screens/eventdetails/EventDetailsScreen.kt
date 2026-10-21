package com.eventroute.app.ui.screens.eventdetails

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.R
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun EventDetailsScreen(
    eventId: String,
    onRegisterClick: (String) -> Unit,
    onPlanJourneyClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenVenueMap: (String) -> Unit = {},
    viewModel: EventDetailsViewModel = viewModel()
) {
    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize().background(ClayBackground)) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                    }
                    LoadingState(message = "Loading event details...")
                }
            }
            is UiState.Empty -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                    }
                    EmptyState(title = "Event not found", message = "This event may no longer exist.")
                }
            }
            is UiState.Error -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                    }
                    ErrorState(message = state.message, onRetry = { viewModel.loadEvent(eventId) })
                }
            }
            is UiState.Success -> {
                val event = state.data
                val banner = if (event.id.contains("2222") || event.title.contains("Robo", ignoreCase = true)) {
                    R.drawable.robocup_banner
                } else {
                    R.drawable.hackx_banner
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 130.dp)
                ) {
                    // ── Hero (image + overlapping stats card) ──
                    Box(Modifier.fillMaxWidth().height(310.dp)) {
                        Box(Modifier.fillMaxWidth().height(270.dp)) {
                            Image(
                                painterResource(banner),
                                contentDescription = event.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.50f)),
                                        startY = 260f
                                    )
                                )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back", color = Color.White.copy(alpha = 0.92f))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ClayIconButton(
                                        if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        { viewModel.toggleSave(event.id) },
                                        contentDescription = if (isSaved) "Remove from saved" else "Save event",
                                        color = Color.White.copy(alpha = 0.92f),
                                        tint = if (isSaved) ClayPinkButton else ClayTextPrimary
                                    )
                                    ClayIconButton(
                                        Icons.Rounded.Share,
                                        {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, event.title)
                                                putExtra(Intent.EXTRA_TEXT, "${event.title} - https://eventroute.app/event/${event.id}")
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Event"))
                                        },
                                        contentDescription = "Share event",
                                        color = Color.White.copy(alpha = 0.92f)
                                    )
                                }
                            }
                            Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 52.dp)) {
                                val tagText = if (event.participationType == "solo") "Solo Registration" else "Team Registration"
                                ClayTag(tagText, ClayPinkTag, ClayPinkText)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        event.title,
                                        style = ClayType.Title.copy(
                                            fontSize = 28.sp, color = Color.White,
                                            shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 8f)
                                        )
                                    )
                                    val feeTag = if (event.isFree || event.feeAmount <= 0) "Free" else "₹${event.feeAmount.toInt()} / Team"
                                    ClayTag(feeTag, ClayAmberLight, ClayAmberText)
                                }
                            }
                        }

                        ClayCard(
                            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp),
                            RoundedCornerShape(30.dp), depth = 12.dp
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatColumn("DEADLINE", event.registrationDeadline, Color(0xFFE11D48), Modifier.weight(1f))
                                Box(Modifier.width(1.dp).height(30.dp).background(ClayCardBorder))
                                StatColumn("SEATS", "${event.seatsLeft} Left", ClayTextPrimary, Modifier.weight(1f))
                                Box(Modifier.width(1.dp).height(30.dp).background(ClayCardBorder))
                                StatColumn("ORGANIZER", event.organizerName, ClayTextPrimary, Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Column(Modifier.padding(horizontal = 22.dp)) {
                        Text("About Event", style = ClayType.Section)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            event.description.ifEmpty {
                                "Join the biggest inter-college hackathon in Delhi-NCR! 24 hours of coding, networking, and awesome prizes. Build solutions for smart mobility and urban transit."
                            },
                            style = ClayType.Body
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    // ── Smart Travel Plan ──
                    ClayCard(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        RoundedCornerShape(32.dp), backgroundColor = ClayBluePrimary, depth = 12.dp
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ClayIconBadge(
                                    Icons.Rounded.Schedule, background = Color(0xFFE6F2FD), tint = ClayBlueText,
                                    size = 46.dp, shape = RoundedCornerShape(16.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Smart Travel Plan", style = ClayType.BodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                                    Text("Based on your location: NSUT Campus", style = ClayType.Caption.copy(fontSize = 11.sp, color = ClayBlueDeep))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            ClayWell(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), Color(0xFFD3E8FA)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("LEAVE BY", style = ClayType.Label.copy(color = ClayBlueDeep.copy(alpha = 0.8f)))
                                        Text(event.smartLeaveBy ?: "08:15 AM", style = ClayType.Heading.copy(fontSize = 24.sp, color = ClayBlueText))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("TRAVEL TIME", style = ClayType.Label.copy(color = ClayBlueDeep.copy(alpha = 0.8f)))
                                        Text("${event.smartTravelMinutes ?: 45} min", style = ClayType.Heading.copy(fontSize = 20.sp, color = ClayBlueText))
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            ClaySmallButton(
                                "Preview Step-by-Step Route", { onPlanJourneyClick(event.id) },
                                Modifier.fillMaxWidth(), Color.White, ClayBlueText
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Venue ──
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ClaySectionHeader(
                            title = "Venue",
                            action = "Expand",
                            onAction = { onOpenVenueMap(event.id) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clay(RoundedCornerShape(30.dp), ClayInsetBg, depth = 10.dp)
                                .clip(RoundedCornerShape(30.dp))
                        ) {
                            if (event.venueLat != 0.0 && event.venueLng != 0.0) {
                                val venuePos = LatLng(event.venueLat, event.venueLng)
                                val cameraPositionState = rememberCameraPositionState {
                                    position = CameraPosition.fromLatLngZoom(venuePos, 14.5f)
                                }
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    googleMapOptionsFactory = {
                                        GoogleMapOptions().liteMode(true)
                                    },
                                    properties = MapProperties(
                                        mapStyleOptions = try {
                                            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_clay)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    ),
                                    uiSettings = MapUiSettings(
                                        scrollGesturesEnabled = false,
                                        zoomGesturesEnabled = false,
                                        tiltGesturesEnabled = false,
                                        rotationGesturesEnabled = false,
                                        mapToolbarEnabled = false,
                                        zoomControlsEnabled = false,
                                        myLocationButtonEnabled = false
                                    )
                                ) {
                                    Marker(
                                        state = remember(venuePos) { MarkerState(position = venuePos) },
                                        title = event.venueName.ifEmpty { event.title },
                                        snippet = event.collegeName
                                    )
                                }
                                // Transparent clickable overlay to consume map taps and navigate in-app
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            onOpenVenueMap(event.id)
                                        }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ClaySurfaceSecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Location details unavailable", style = ClayType.Caption)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.Rounded.LocationOn, null, tint = ClayBlueDeep, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            val displayAddress = listOf(event.venueName, event.collegeName)
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                                .ifEmpty { event.venueAddress.ifEmpty { "Venue location details TBA" } }
                            Text(displayAddress, style = ClayType.Body.copy(fontSize = 12.sp))
                        }
                    }
                }

                ClayStickyBar(Modifier.align(Alignment.BottomCenter)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("TEAM SIZE", style = ClayType.Label)
                            val teamSizeText = if (event.participationType == "solo") "Solo" else "${event.minTeamSize} - ${event.maxTeamSize} Members"
                            Text(teamSizeText, style = ClayType.BodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.width(24.dp))
                        ClayPillButton("Register Now", { onRegisterClick(event.id) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = ClayType.Label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = ClayType.BodyStrong.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor))
    }
}
