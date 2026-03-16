package com.islandskiesastro.astroplanner

data class DefaultEquipment(
    val telescope: Telescope,
    val opticalElement: OpticalElement,
    val camera: Camera
)

object EquipmentRepository {
    val telescopes = listOf(
        Telescope("Celestron C8", focalLength = 2032.0, aperture = 203.2),
        Telescope("RedCat 61",    focalLength = 360.0,  aperture = 61.0)
    )
    val cameras = listOf(
        Camera("ZWO ASI294MC Pro",  19.1, 13.0, 4.63, 4144, 2822),
        Camera("ZWO ASI2600MC Duo", 23.5, 15.7, 3.76, 6248, 4176)
    )
    val opticalElements = listOf(
        OpticalElement("None",          factor = 1.0),
        OpticalElement("0.63x Reducer", factor = 0.63)
    )

    // Default setup — will be user-configurable in the future
    val defaultEquipment = DefaultEquipment(
        telescope     = telescopes.first { it.displayName == "Celestron C8" },
        opticalElement = opticalElements.first { it.displayName == "0.63x Reducer" },
        camera        = cameras.first { it.displayName == "ZWO ASI294MC Pro" }
    )
}
