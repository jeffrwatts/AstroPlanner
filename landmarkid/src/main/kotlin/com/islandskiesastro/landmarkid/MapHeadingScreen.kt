package com.islandskiesastro.landmarkid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapType
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

// Used only until the map reports its first real viewport (see visibleCorners below).
private const val SIGHT_LINE_FALLBACK_METERS = 8_000.0

// Overshoot past the viewport's farthest corner so the line always reaches the screen edge
// regardless of bearing; anything beyond the edge is simply clipped by the map view.
private const val SIGHT_LINE_MARGIN = 1.2

@Composable
fun MapHeadingScreen(location: LocationData?, heading: Double, modifier: Modifier = Modifier) {
    if (location == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Waiting for GPS fix…", color = Color.Gray)
        }
        return
    }

    val here = LatLng(location.latitude, location.longitude)
    var visibleCorners by remember { mutableStateOf<List<LatLng>?>(null) }

    val sightLineEnd = remember(here, heading, visibleCorners) {
        val distance = visibleCorners
            ?.maxOf { corner -> distanceMeters(here, corner) }
            ?.times(SIGHT_LINE_MARGIN)
            ?: SIGHT_LINE_FALLBACK_METERS
        destinationPoint(here, heading, distance)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(here, 14f)
    }

    LaunchedEffect(here) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLng(here))
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.SATELLITE),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
    ) {
        Marker(state = rememberMarkerState(position = here), title = "You")
        Polyline(
            points = listOf(here, sightLineEnd),
            color = Color(0xFFFFC107),
            width = 6f
        )

        MapEffect(Unit) { map ->
            map.setOnCameraIdleListener {
                val region = map.projection.visibleRegion
                visibleCorners = listOf(region.farLeft, region.farRight, region.nearLeft, region.nearRight)
            }
        }
    }
}
