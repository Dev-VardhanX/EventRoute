package com.eventroute.app.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.eventroute.app.R
import com.eventroute.app.domain.model.Leg
import com.eventroute.app.domain.model.LegMode
import com.eventroute.app.domain.model.Route
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

fun Leg.colorOrDefault(): Color {
    val hex = transitColorHex
    if (!hex.isNullOrBlank()) {
        try {
            val clean = hex.removePrefix("#")
            val colorLong = clean.toLong(16)
            return if (clean.length == 6) Color(0xFF000000 or colorLong) else Color(colorLong)
        } catch (_: Exception) {}
    }
    return when (mode) {
        LegMode.METRO -> Color(0xFF3B82F6)
        LegMode.BUS -> Color(0xFF10B981)
        LegMode.DRIVE -> Color(0xFFF59E0B)
        else -> Color(0xFF6366F1)
    }
}

@Composable
fun RouteGoogleMap(
    route: Route,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val camera = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    val all = remember(route) { route.legs.flatMap { it.points }.map { LatLng(it.lat, it.lng) } }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        onMapLoaded = { loaded = true },
        properties = MapProperties(
            mapStyleOptions = try {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_clay)
            } catch (e: Exception) {
                null
            }
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        ),
    ) {
        route.legs.forEach { leg ->
            val pts = leg.points.map { LatLng(it.lat, it.lng) }
            val walk = leg.mode == LegMode.WALK
            Polyline(
                points = pts,
                color = if (walk) Color(0xFF94A3B8) else leg.colorOrDefault(),
                width = if (walk) 10f else 16f,
                pattern = if (walk) listOf(Dot(), Gap(12f)) else null,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
        }
        all.firstOrNull()?.let { startPos ->
            val startState = remember(startPos) { MarkerState(startPos) }
            Marker(state = startState, title = "Start")
        }
        all.lastOrNull()?.let { venuePos ->
            val venueState = remember(venuePos) { MarkerState(venuePos) }
            Marker(state = venueState, title = "Venue")
        }
    }

    LaunchedEffect(loaded, all) {
        if (loaded && all.size >= 2) {
            val b = LatLngBounds.builder().apply { all.forEach(::include) }.build()
            camera.animate(CameraUpdateFactory.newLatLngBounds(b, 140))
        }
    }
}
