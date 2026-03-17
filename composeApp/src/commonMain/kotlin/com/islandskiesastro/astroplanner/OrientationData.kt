package com.islandskiesastro.astroplanner

data class OrientationData(
    val altitude: Double,   // degrees, -90..+90
    val azimuth: Double,    // magnetic north, 0..360
    val accuracy: Int       // 0 = unreliable, 3 = high (Android SENSOR_STATUS scale)
)
