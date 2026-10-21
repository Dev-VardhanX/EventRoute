package com.eventroute.app.ui.screens.startpoint

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TeamStartPointScreen(
    teamId: String,
    onBack: () -> Unit,
    onPlanUpdated: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeamStartPointViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf("college") } // "college", "home", "saved", "current", "pin"
    var selectedPoint by remember { mutableStateOf(GeoPoint(28.6094, 77.0368)) }
    var selectedLabel by remember { mutableStateOf("NSUT Dwarka Campus") }
    var selectedPref by remember { mutableStateOf("Fastest") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(28.6094, 77.0368), 12f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ClayTopBar("Choose Start Point", onBack)
        Spacer(Modifier.height(18.dp))

        Text("Select Departure Location", style = ClayType.Section, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(12.dp))

        // Options
        StartOptionCard(
            title = "College Campus (Default)",
            subtitle = "NSUT Dwarka Campus",
            icon = Icons.Rounded.School,
            selected = selectedMode == "college",
            onClick = {
                selectedMode = "college"
                selectedPoint = GeoPoint(28.6094, 77.0368)
                selectedLabel = "NSUT Dwarka Campus"
            }
        )
        Spacer(Modifier.height(12.dp))

        StartOptionCard(
            title = "Home",
            subtitle = "Rohini Sector 14, Delhi",
            icon = Icons.Rounded.Home,
            selected = selectedMode == "home",
            onClick = {
                selectedMode = "home"
                selectedPoint = GeoPoint(28.7100, 77.1200)
                selectedLabel = "Rohini Sector 14"
            }
        )
        Spacer(Modifier.height(12.dp))

        StartOptionCard(
            title = "Saved Place",
            subtitle = "Connaught Place, Central Delhi",
            icon = Icons.Rounded.Bookmark,
            selected = selectedMode == "saved",
            onClick = {
                selectedMode = "saved"
                selectedPoint = GeoPoint(28.6315, 77.2167)
                selectedLabel = "Connaught Place"
            }
        )
        Spacer(Modifier.height(12.dp))

        StartOptionCard(
            title = "Current GPS Location",
            subtitle = if (selectedMode == "current") selectedLabel else "Uses Fused Location Provider",
            icon = Icons.Rounded.MyLocation,
            selected = selectedMode == "current",
            onClick = {
                selectedMode = "current"
                coroutineScope.launch {
                    val fix = viewModel.getFusedLocation()
                    selectedPoint = fix
                    selectedLabel = "Current Location (${String.format("%.4f", fix.lat)}, ${String.format("%.4f", fix.lng)})"
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        StartOptionCard(
            title = "Drop a Pin on Map",
            subtitle = if (selectedMode == "pin") selectedLabel else "Tap anywhere on map below",
            icon = Icons.Rounded.Place,
            selected = selectedMode == "pin",
            onClick = { selectedMode = "pin" }
        )

        if (selectedMode == "pin") {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clay(RoundedCornerShape(26.dp), Color.White, depth = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng ->
                        selectedPoint = GeoPoint(latLng.latitude, latLng.longitude)
                        coroutineScope.launch {
                            val label = withContext(Dispatchers.IO) {
                                runCatching {
                                    val geocoder = Geocoder(context)
                                    val list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                    list?.firstOrNull()?.let {
                                        it.thoroughfare ?: it.subLocality ?: it.locality ?: it.getAddressLine(0)
                                    } ?: "Dropped pin"
                                }.getOrElse { "Dropped pin" }
                            }
                            selectedLabel = label
                        }
                    }
                ) {
                    val markerState = remember(selectedPoint) {
                        MarkerState(position = LatLng(selectedPoint.lat, selectedPoint.lng))
                    }
                    Marker(
                        state = markerState,
                        title = selectedLabel
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Travel Preference", style = ClayType.Section, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(12.dp))

        val prefs = listOf("Fastest", "Cheapest", "Fewest transfers")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            prefs.forEach { pref ->
                val active = selectedPref.equals(pref, ignoreCase = true)
                ClaySmallButton(
                    text = pref,
                    onClick = { selectedPref = pref },
                    modifier = Modifier.weight(1f),
                    backgroundColor = if (active) ClayBluePrimary else Color.White,
                    textColor = if (active) ClayBlueText else ClayTextSecondary
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        ClayPillButton(
            text = if (isSaving) "Updating Plan..." else "Save & Recalculate Plan",
            onClick = {
                viewModel.updateStartPoint(
                    teamId = teamId,
                    lat = selectedPoint.lat,
                    lng = selectedPoint.lng,
                    label = selectedLabel,
                    pref = selectedPref,
                    onComplete = { newVersion ->
                        onPlanUpdated(newVersion)
                        onBack()
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving
        )

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun StartOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val source = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayPress(onClick, true, source)
            .clay(shape, if (selected) Color(0xFFF0F7FF) else Color.White, depth = if (selected) 6.dp else 9.dp)
            .then(
                if (selected) Modifier.border(2.dp, ClayBlueDeep, shape) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayIconBadge(
            icon = icon,
            background = if (selected) ClayBluePrimary else ClaySurfaceSecondary,
            tint = if (selected) ClayBlueText else ClayTextPrimary,
            size = 44.dp,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ClayType.BodyStrong.copy(fontSize = 14.sp))
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = ClayType.Caption.copy(fontSize = 11.sp, color = ClayTextMuted))
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, "Selected", tint = ClayBlueDeep, modifier = Modifier.size(22.dp))
        }
    }
}
