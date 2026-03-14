package com.islandskiesastro.astroplanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

actual class LocationService : NSObject(), CLLocationManagerDelegateProtocol {
    private val locationManager = CLLocationManager()
    private val _location = MutableStateFlow<LocationData?>(null)
    actual val location: StateFlow<LocationData?> = _location.asStateFlow()

    init {
        locationManager.delegate = this
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
    }

    actual fun startUpdates() {
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    actual fun stopUpdates() {
        locationManager.stopUpdatingLocation()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val clLocation = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        _location.value = LocationData(
            latitude = clLocation.coordinate.useContents { latitude },
            longitude = clLocation.coordinate.useContents { longitude },
            altitude = clLocation.altitude
        )
    }
}
