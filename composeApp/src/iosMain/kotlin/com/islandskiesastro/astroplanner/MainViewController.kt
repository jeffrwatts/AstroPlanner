package com.islandskiesastro.astroplanner

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val locationService = remember { LocationService() }
    val driverFactory = remember { DatabaseDriverFactory() }
    val repository = remember { CelestialObjectRepository(driverFactory) }
    App(locationService, hasLocationPermission = true, repository = repository)
}
