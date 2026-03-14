package com.islandskiesastro.astroplanner

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val locationService = remember { LocationService() }
    App(locationService, hasLocationPermission = true)
}
