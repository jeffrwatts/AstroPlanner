package com.islandskiesastro.astroplanner

object EquipmentRepository {
    val telescopes = listOf(
        Telescope("Celestron C8", focalLength = 2032.0, aperture = 203.2),
        Telescope("RedCat 61",    focalLength = 360.0,  aperture = 61.0)
    )
    val cameras = listOf(
        Camera("ZWO ASI294MC Pro",  19.1, 13.0, 4.63, 4144, 2822),
        Camera("ZWO ASI585MC Pro",  11.2,  6.3, 2.90, 3840, 2160),
        Camera("ZWO ASI2600MC Duo", 23.5, 15.7, 3.76, 6248, 4176)
    )
    val opticalElements = listOf(
        OpticalElement("None",          factor = 1.0),
        OpticalElement("0.63x Reducer", factor = 0.63)
    )
}
