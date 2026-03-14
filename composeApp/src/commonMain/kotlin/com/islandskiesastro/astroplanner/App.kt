package com.islandskiesastro.astroplanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun App(locationService: LocationService, hasLocationPermission: Boolean) {
    MaterialTheme {
        MainScreen(locationService, hasLocationPermission)
    }
}
