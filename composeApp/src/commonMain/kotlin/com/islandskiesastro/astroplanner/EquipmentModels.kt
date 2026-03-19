package com.islandskiesastro.astroplanner

data class EquipmentConfig(
    val id: Long = 0L,
    val name: String,
    val focalLength: Double,          // mm — telescope focal length
    val aperture: Double,             // mm — telescope aperture (display only)
    val focalReducerFactor: Double,   // 1.0 = no reducer
    val pixelSize: Double,            // µm — camera pixel size
    val resolutionWidth: Int,         // px
    val resolutionHeight: Int         // px
)
