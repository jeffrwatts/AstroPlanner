package com.islandskiesastro.astroplanner

data class Telescope(val displayName: String, val focalLength: Double, val aperture: Double)

data class Camera(
    val displayName: String,
    val sensorWidth: Double, val sensorHeight: Double,
    val pixelSize: Double,
    val resolutionWidth: Int, val resolutionHeight: Int
)

data class OpticalElement(val displayName: String, val factor: Double)
