package com.islandskiesastro.astroplanner

actual object AstronomyService {
    actual fun getAltAzm(ra: Double, dec: Double, lat: Double, lon: Double, altMeters: Double): AltAzmData =
        AltAzmData(altitude = 0.0, azimuth = 0.0)

    actual fun getPlanetRaDec(planetObjectId: String): Pair<Double, Double> =
        Pair(0.0, 0.0)

    actual fun getMeridianTransit(ra: Double, lon: Double, lat: Double): String =
        "--:--"
}
