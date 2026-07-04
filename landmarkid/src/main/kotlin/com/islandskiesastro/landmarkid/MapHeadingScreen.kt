package com.islandskiesastro.landmarkid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

// Length of the projected sight line drawn out from the boat toward the current heading.
private const val SIGHT_LINE_METERS = 8_000.0

@Composable
fun MapHeadingScreen(location: LocationData?, heading: Double, modifier: Modifier = Modifier) {
    if (location == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Waiting for GPS fix…", color = Color.Gray)
        }
        return
    }

    val here = LatLng(location.latitude, location.longitude)
    val sightLineEnd = remember(here, heading) { destinationPoint(here, heading, SIGHT_LINE_METERS) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(here, 14f)
    }

    LaunchedEffect(here) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLng(here))
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
    ) {
        Marker(state = rememberMarkerState(position = here), title = "You")
        Polyline(
            points = listOf(here, sightLineEnd),
            color = Color(0xFFFFC107),
            width = 6f
        )
    }
}
