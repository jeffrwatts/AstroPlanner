package com.islandskiesastro.astroplanner

import kotlinx.coroutines.flow.StateFlow

expect class OrientationService {
    val orientationData: StateFlow<OrientationData>
    fun startUpdates()
    fun stopUpdates()
}

expect fun computeMagneticDeclination(lat: Double, lon: Double, altMeters: Double): Double
