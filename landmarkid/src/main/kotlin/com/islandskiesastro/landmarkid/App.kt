package com.islandskiesastro.landmarkid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun App(
    locationService: LocationService,
    orientationService: OrientationService,
    hasPermission: Boolean
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Camera and location permissions are required to use LandmarkID.",
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
                return@Surface
            }

            DisposableEffect(Unit) {
                locationService.startUpdates()
                orientationService.startUpdates()
                onDispose {
                    locationService.stopUpdates()
                    orientationService.stopUpdates()
                }
            }

            val location by locationService.location.collectAsState()
            val orientation by orientationService.orientationData.collectAsState()
            val declination = location?.let {
                computeMagneticDeclination(it.latitude, it.longitude, it.altitude)
            } ?: 0.0
            val trueHeading = (orientation.azimuth + declination + 360.0) % 360.0

            Column(modifier = Modifier.fillMaxSize()) {
                CameraHeadingScreen(
                    heading = trueHeading,
                    altitude = orientation.altitude,
                    accuracy = orientation.accuracy,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                MapHeadingScreen(
                    location = location,
                    heading = trueHeading,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
