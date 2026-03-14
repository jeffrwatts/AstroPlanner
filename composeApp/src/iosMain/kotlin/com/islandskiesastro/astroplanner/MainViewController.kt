package com.islandskiesastro.astroplanner

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val locationService = remember { LocationService() }
    val driverFactory = remember { DatabaseDriverFactory() }
    val imageStorage = remember { ImageStorage() }
    val repository = remember { CelestialObjectRepository(driverFactory, imageStorage) }
    App(locationService, hasLocationPermission = true, repository = repository)
}
