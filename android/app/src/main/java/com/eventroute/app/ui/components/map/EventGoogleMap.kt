package com.eventroute.app.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.R
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState

import com.google.maps.android.compose.CameraPositionState

data class EventClusterItem(val event: Event) : ClusterItem {
    override fun getPosition() = LatLng(event.venueLat, event.venueLng)
    override fun getTitle() = event.title
    override fun getSnippet() = event.collegeName
    override fun getZIndex() = 0f
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun EventGoogleMap(
    events: List<Event>,
    userLocation: GeoPoint?,
    hasLocationPermission: Boolean,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(28.6139, 77.2090), 10.5f) // Delhi
    }
) {
    val context = LocalContext.current
    val camera = cameraPositionState
    val items = remember(events) { events.map(::EventClusterItem) }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 11.5f))
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapStyleOptions = try {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_clay)
            } catch (e: Exception) {
                null
            },
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        ),
    ) {
        Clustering(
            items = items,
            onClusterItemClick = {
                onEventClick(it.event.id)
                true
            },
            clusterItemContent = { item ->
                val color = when (item.event.category.lowercase()) {
                    "hackathon", "coding" -> Color(0xFF3B82F6) // Blue
                    "robotics" -> Color(0xFF10B981) // Green
                    "cultural", "open mic" -> Color(0xFFEC4899) // Pink
                    else -> Color(0xFF8B5CF6) // Purple
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.event.title.take(1),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        )
    }
}
