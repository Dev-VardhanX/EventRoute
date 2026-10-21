package com.eventroute.app.ui.screens.venuemap

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.R
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@Composable
fun VenueMapScreen(
    eventId: String,
    onPlanRoute: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VenueMapViewModel = viewModel()
) {
    LaunchedEffect(eventId) {
        viewModel.loadVenue(eventId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Box(modifier = modifier.fillMaxSize().background(ClayBackground)) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    ClayTopBar("Venue Map", onBack)
                    Spacer(Modifier.height(24.dp))
                    LoadingState(message = "Loading venue location...")
                }
            }
            is UiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    ClayTopBar("Venue Map", onBack)
                    Spacer(Modifier.height(24.dp))
                    EmptyState(title = "Location unavailable", message = "No venue coordinates available for this event.")
                }
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    ClayTopBar("Venue Map", onBack)
                    Spacer(Modifier.height(24.dp))
                    ErrorState(message = state.message, onRetry = { viewModel.loadVenue(eventId) })
                }
            }
            is UiState.Success -> {
                val event = state.data
                val venuePos = LatLng(event.venueLat, event.venueLng)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(venuePos, 15f)
                }

                val hasLocationPermission = remember {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                }

                // Interactive full-screen GoogleMap
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                        mapStyleOptions = try {
                            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_clay)
                        } catch (e: Exception) {
                            null
                        }
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = false,
                        myLocationButtonEnabled = hasLocationPermission,
                        mapToolbarEnabled = false
                    )
                ) {
                    Marker(
                        state = remember(venuePos) { MarkerState(position = venuePos) },
                        title = event.venueName.ifEmpty { event.title },
                        snippet = event.collegeName
                    )
                }

                // Top-start back / collapse button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 12.dp)
                ) {
                    ClayIconButton(
                        icon = Icons.Rounded.FullscreenExit,
                        onClick = onBack,
                        contentDescription = "Exit Venue Map",
                        size = 52.dp,
                        color = Color.White,
                        tint = ClayBlueDeep
                    )
                }

                // Bottom venue card with route and directions buttons
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        depth = 12.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = event.venueName.ifEmpty { event.title },
                                style = ClayType.CardTitle.copy(fontSize = 18.sp)
                            )
                            val displayAddress = listOf(event.venueName, event.collegeName)
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                                .ifEmpty { event.venueAddress.ifEmpty { "Venue location details TBA" } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = ClayBlueDeep,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = displayAddress,
                                    style = ClayType.Body.copy(fontSize = 13.sp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClayPillButton(
                                    text = "Plan my route",
                                    onClick = { onPlanRoute(event.id) },
                                    modifier = Modifier.weight(1f)
                                )
                                ClayPillButton(
                                    text = "Get directions",
                                    onClick = {
                                        val venueLabel = event.venueName.ifEmpty { event.title }
                                        val geoUri = Uri.parse("geo:${event.venueLat},${event.venueLng}?q=${event.venueLat},${event.venueLng}(${Uri.encode(venueLabel)})")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: ActivityNotFoundException) {
                                            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${event.venueLat},${event.venueLng}")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                        }
                                    },
                                    backgroundColor = Color.White,
                                    textColor = ClayBlueDeep,
                                    modifier = Modifier.wrapContentWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
