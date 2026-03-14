package com.islandskiesastro.astroplanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun App(locationService: LocationService, hasLocationPermission: Boolean) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        MainScreen(locationService, hasLocationPermission)
    }
}
