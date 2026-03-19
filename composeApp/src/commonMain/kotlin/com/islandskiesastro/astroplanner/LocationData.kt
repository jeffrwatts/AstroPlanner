package com.islandskiesastro.astroplanner

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Double
)

data class SavedLocation(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timeZoneId: String   // IANA ID, e.g. "Pacific/Honolulu"
) {
    fun toLocationData() = LocationData(latitude, longitude, altitude, accuracy = 0.0)
}

val SAVED_LOCATIONS = listOf(
    SavedLocation("Mauna Kea Visitor Center", 19.8207, -155.4680, 2804.0, "Pacific/Honolulu"),
    SavedLocation("Home", 19.7065, -155.9771, 470.0, "Pacific/Honolulu"),
    SavedLocation("Seattle, WA", 47.6062, -122.3321, 56.0, "America/Los_Angeles")
)
