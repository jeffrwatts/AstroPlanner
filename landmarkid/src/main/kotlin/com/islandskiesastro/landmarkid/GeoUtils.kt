package com.islandskiesastro.landmarkid

import com.google.android.gms.maps.model.LatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun destinationPoint(origin: LatLng, bearingDegrees: Double, distanceMeters: Double): LatLng {
    val bearing = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(origin.latitude)
    val lon1 = Math.toRadians(origin.longitude)
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS

    val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )

    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
