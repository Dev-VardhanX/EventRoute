package com.eventroute.app.ui.screens.livejourney

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.components.map.LiveTeamMap
import com.eventroute.app.ui.theme.*
import com.eventroute.app.util.rememberLocationPermissionState

/**
 * Live team tracking: teammates on the route, destination target, and dismissible traffic alert.
 * Real foreground location service with persistent notification and simulated team feed.
 */
@Composable
fun LiveJourneyScreen(
    eventId: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveJourneyViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(eventId) {
        viewModel.loadLiveJourney(eventId = eventId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSharing by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    var showAlert by remember { mutableStateOf(true) }
    var showConsentDialog by remember { mutableStateOf(false) }

    val locationPermission = rememberLocationPermissionState {
        viewModel.startLocationSharing(context)
    }

    val liveData = (uiState as? UiState.Success)?.data
    val members = liveData?.members ?: emptyList()
    val venueLocation = liveData?.venueLocation ?: GeoPoint(28.5459, 77.1926)
    val venueName = liveData?.venueName ?: "IIT Delhi"
    val targetArrivalAt = liveData?.targetArrivalAt ?: "09:30 AM"
    val alertMessage = liveData?.alertMessage

    // Reactively show alert if simulator injects one
    LaunchedEffect(alertMessage) {
        if (!alertMessage.isNullOrBlank()) {
            showAlert = true
        }
    }

    val currentUser = members.firstOrNull { it.isLeader || it.name.contains("You") }
    val userStatus = currentUser?.journeyStatus ?: "traveling"

    Box(modifier = modifier.fillMaxSize().background(ClayBackground)) {
        LiveTeamMap(
            members = members,
            venueLocation = venueLocation,
            venueName = venueName,
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar & Alert Banner
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back", size = 46.dp)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sharing Status Pill / Start Button
                    if (isSharing) {
                        Row(
                            Modifier
                                .clay(RoundedCornerShape(50), ClayGreenPrimary, depth = 9.dp)
                                .clayPress({ viewModel.stopLocationSharing(context) }, true, remember { MutableInteractionSource() })
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.MyLocation, "Sharing location", tint = ClayGreenText, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sharing • Stop", style = ClayType.BodyStrong.copy(fontSize = 12.sp, color = ClayGreenInk))
                        }
                    } else {
                        Row(
                            Modifier
                                .clay(RoundedCornerShape(50), ClayBluePrimary, depth = 9.dp)
                                .clayPress({ showConsentDialog = true }, true, remember { MutableInteractionSource() })
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.MyLocation, "Start sharing", tint = ClayBlueDeep, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share GPS", style = ClayType.BodyStrong.copy(fontSize = 12.sp, color = ClayBlueDeep))
                        }
                    }

                    // Target Venue Pill
                    Row(
                        Modifier
                            .clay(RoundedCornerShape(50), Color.White, depth = 9.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, tint = ClayBlueDeep, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(venueName, style = ClayType.BodyStrong.copy(fontSize = 12.sp, color = ClayBlueDeep))
                            Text(targetArrivalAt, style = ClayType.Label.copy(fontSize = 9.sp, color = ClayTextMuted))
                        }
                    }
                }
            }

            if (showAlert && !alertMessage.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clay(RoundedCornerShape(30.dp), ClayAlertPink, depth = 11.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayIconBadge(Icons.Rounded.Warning, background = Color(0xFFFCD5E4), tint = Color(0xFFDB2777), size = 42.dp, shape = RoundedCornerShape(15.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TRAFFIC ALERT", style = ClayType.Label.copy(fontSize = 9.sp, color = ClayPinkText))
                        Text(alertMessage, style = ClayType.BodyStrong.copy(fontSize = 14.sp, color = ClayPinkText))
                    }
                    Icon(
                        Icons.Rounded.Close, "Dismiss alert", tint = ClayPinkText,
                        modifier = Modifier
                            .size(36.dp)
                            .clayPress({ showAlert = false }, true, remember { MutableInteractionSource() })
                            .padding(8.dp)
                    )
                }
            }
        }

        // Bottom Controls: Quick Status Chips + ETA Card
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 18.dp, end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick Status Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickStatusChip(
                    label = "On my way",
                    isSelected = userStatus.equals("traveling", true) || userStatus.equals("idle", true),
                    selectedBg = ClayGreenPrimary,
                    selectedText = ClayGreenText,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateUserStatus("traveling") }
                )
                QuickStatusChip(
                    label = "Running late",
                    isSelected = userStatus.equals("delayed", true) || userStatus.equals("late", true),
                    selectedBg = ClayAlertPink,
                    selectedText = ClayPinkText,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateUserStatus("delayed") }
                )
                QuickStatusChip(
                    label = "Reached",
                    isSelected = userStatus.equals("arrived", true) || userStatus.equals("reached", true),
                    selectedBg = ClayBluePrimary,
                    selectedText = ClayBlueText,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.updateUserStatus("arrived")
                        viewModel.stopLocationSharing(context)
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Arrival ETA banner
            Row(
                Modifier
                    .clay(RoundedCornerShape(50), Color.White, depth = 12.dp)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = ClayBlueDeep, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Arrive by 09:30 AM", style = ClayType.BodyStrong.copy(fontSize = 14.sp))
                    val statusText = if (userStatus.equals("arrived", true)) "ARRIVED AT VENUE" else "LIVE TEAM TRACKING"
                    Text(statusText, style = ClayType.Label.copy(fontSize = 9.sp, color = ClayBlueDeep))
                }
            }
        }

        // Consent Dialog
        if (showConsentDialog) {
            Dialog(onDismissRequest = { showConsentDialog = false }) {
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    backgroundColor = Color.White,
                    depth = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ClayIconBadge(
                            icon = Icons.Rounded.MyLocation,
                            background = Color(0xFFD3E8FA),
                            tint = ClayBlueDeep,
                            size = 56.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Share Live Location",
                            style = ClayType.Title.copy(fontSize = 20.sp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your live location is visible only to your team until you stop sharing or arrive.",
                            style = ClayType.Caption.copy(fontSize = 13.sp, color = ClayTextMuted),
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ClaySmallButton(
                                text = "Cancel",
                                onClick = { showConsentDialog = false },
                                modifier = Modifier.weight(1f),
                                backgroundColor = Color(0xFFF1F5F9),
                                textColor = ClayTextMuted
                            )
                            ClaySmallButton(
                                text = "Start",
                                onClick = {
                                    showConsentDialog = false
                                    if (locationPermission.hasPermission) {
                                        viewModel.startLocationSharing(context)
                                    } else {
                                        locationPermission.requestPermission()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                backgroundColor = ClayBluePrimary,
                                textColor = ClayBlueText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatusChip(
    label: String,
    isSelected: Boolean,
    selectedBg: Color,
    selectedText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) selectedBg else Color.White
    val textColor = if (isSelected) selectedText else ClayTextMuted
    val depth = if (isSelected) 6.dp else 4.dp

    Box(
        modifier = modifier
            .clay(RoundedCornerShape(20.dp), bg, depth = depth)
            .clayPress(onClick, true, remember { MutableInteractionSource() })
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = ClayType.BodyStrong.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        )
    }
}
