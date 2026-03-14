package com.islandskiesastro.astroplanner

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class LocationService(context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val _location = MutableStateFlow<LocationData?>(null)
    actual val location: StateFlow<LocationData?> = _location.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                _location.value = LocationData(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    actual fun startUpdates() {
        val request = LocationRequest.Builder(10_000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    actual fun stopUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
