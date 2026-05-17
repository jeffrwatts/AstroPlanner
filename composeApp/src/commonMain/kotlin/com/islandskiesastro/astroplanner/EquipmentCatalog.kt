package com.islandskiesastro.astroplanner

data class OtaSpec(
    val name: String,
    val focalLength: Double,
    val aperture: Double,
    val filtersSupported: Boolean = true,
    val builtInCamera: CameraSpec? = null  // non-null disables camera/reducer selection
)

data class CameraSpec(
    val name: String,
    val pixelSize: Double,
    val resolutionWidth: Int,
    val resolutionHeight: Int
)

data class ReducerSpec(
    val label: String,
    val factor: Double
)

object EquipmentCatalog {

    val otas = listOf(
        OtaSpec("Celestron C8",             focalLength = 2032.0, aperture = 203.2),
        OtaSpec("William Optics RedCat 51", focalLength = 250.0,  aperture = 51.0),
        OtaSpec("William Optics RedCat 61", focalLength = 300.0,  aperture = 61.0),
        OtaSpec("William Optics RedCat 71", focalLength = 350.0,  aperture = 71.0),
        OtaSpec("SeeStar S30",    focalLength = 150.0, aperture = 30.0, filtersSupported = false,
            builtInCamera = CameraSpec("Sony IMX462 (built-in)", pixelSize = 2.9, resolutionWidth = 1920,  resolutionHeight = 1080)),
        OtaSpec("SeeStar S30 Pro", focalLength = 160.0, aperture = 30.0, filtersSupported = false,
            builtInCamera = CameraSpec("Sony IMX462 (built-in)", pixelSize = 2.9, resolutionWidth = 3840,  resolutionHeight = 2160)),
        OtaSpec("SeeStar S50",    focalLength = 250.0, aperture = 50.0, filtersSupported = false,
            builtInCamera = CameraSpec("Sony IMX715 (built-in)", pixelSize = 3.45, resolutionWidth = 4056, resolutionHeight = 3040)),
    )

    val cameras = listOf(
        CameraSpec("ZWO ASI 294MC Pro",  pixelSize = 4.63, resolutionWidth = 4144, resolutionHeight = 2822),
        CameraSpec("ZWO ASI 2600MC Duo", pixelSize = 3.76, resolutionWidth = 6280, resolutionHeight = 4210),
    )

    val reducers = listOf(
        ReducerSpec("None (1.0x)",   factor = 1.0),
        ReducerSpec("0.63x Reducer", factor = 0.63),
        ReducerSpec("0.71x Reducer", factor = 0.71),
        ReducerSpec("0.8x Reducer",  factor = 0.8),
        ReducerSpec("1.5x Barlow",   factor = 1.5),
        ReducerSpec("2x Barlow",     factor = 2.0),
        ReducerSpec("3x Barlow",     factor = 3.0),
    )
}
