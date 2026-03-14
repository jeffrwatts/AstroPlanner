package com.islandskiesastro.astroplanner

import kotlinx.coroutines.flow.StateFlow

expect class LocationService {
    val location: StateFlow<LocationData?>
    fun startUpdates()
    fun stopUpdates()
}
