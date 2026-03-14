package com.islandskiesastro.astroplanner

data class AltAzmData(val altitude: Double, val azimuth: Double)

expect object AstronomyService {
    fun getAltAzm(ra: Double, dec: Double, lat: Double, lon: Double, altMeters: Double): AltAzmData
    fun getPlanetRaDec(planetObjectId: String): Pair<Double, Double>
    fun getMeridianTransit(ra: Double, lon: Double, lat: Double): String
}
