package com.eventroute.app.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.R
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.model.TeamMember
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private data class MemberMapItem(
    val member: TeamMember,
    val position: LatLng
)

@Composable
fun LiveTeamMap(
    members: List<TeamMember>,
    venueLocation: GeoPoint?,
    venueName: String? = "Venue",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val defaultPos = LatLng(28.5459, 77.1926) // IIT Delhi default
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(venueLocation?.let { LatLng(it.lat, it.lng) } ?: defaultPos, 12f)
    }
    var loaded by remember { mutableStateOf(false) }

    val memberItems = remember(members, venueLocation) {
        members.mapIndexed { index, m ->
            val pos = if (m.liveLat != null && m.liveLng != null) {
                LatLng(m.liveLat, m.liveLng)
            } else if (m.startLat != null && m.startLng != null) {
                LatLng(m.startLat, m.startLng)
            } else {
                val vLat = venueLocation?.lat ?: 28.5459
                val vLng = venueLocation?.lng ?: 77.1926
                LatLng(vLat + (index + 1) * 0.015, vLng - (index + 1) * 0.015)
            }
            MemberMapItem(m, pos)
        }
    }

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
        venueLocation?.let { venueLoc ->
            val venueState = remember(venueLoc) { MarkerState(LatLng(venueLoc.lat, venueLoc.lng)) }
            Marker(
                state = venueState,
                title = venueName ?: "Venue"
            )
        }

        for (item in memberItems) {
            val member = item.member
            val memberState = remember(item.position) { MarkerState(item.position) }
            val isLate = member.journeyStatus.equals("delayed", ignoreCase = true) ||
                    member.journeyStatus.equals("late", ignoreCase = true)
            val isYou = member.name.contains("John", ignoreCase = true) ||
                    member.name.contains("You", ignoreCase = true)
            val chipColor = when {
                isLate -> Color(0xFFEF4444)
                isYou -> Color(0xFF3B82F6)
                else -> Color(0xFF10B981)
            }
            val label = buildString {
                append(member.name.split(" ").firstOrNull() ?: member.name)
                append(" (")
                append(member.liveEtaAt ?: member.journeyStatus)
                append(")")
            }

            MarkerComposable(
                state = memberState,
                title = member.name
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    LaunchedEffect(loaded, memberItems, venueLocation) {
        if (loaded) {
            val allPoints = mutableListOf<LatLng>()
            venueLocation?.let { allPoints.add(LatLng(it.lat, it.lng)) }
            memberItems.forEach { allPoints.add(it.position) }
            if (allPoints.size >= 2) {
                val bounds = LatLngBounds.builder().apply {
                    allPoints.forEach(::include)
                }.build()
                camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140))
            }
        }
    }
}
