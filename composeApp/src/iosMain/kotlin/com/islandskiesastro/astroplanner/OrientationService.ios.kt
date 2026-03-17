@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.islandskiesastro.astroplanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObject
import kotlin.math.PI

actual class OrientationService : NSObject() {
    private val motionManager = CMMotionManager()
    private val _orientationData = MutableStateFlow(OrientationData(0.0, 0.0, 0))
    actual val orientationData: StateFlow<OrientationData> = _orientationData.asStateFlow()

    actual fun startUpdates() {
        if (!motionManager.isDeviceMotionAvailable()) return
        motionManager.startDeviceMotionUpdatesUsingReferenceFrame(
            CMAttitudeReferenceFrameXMagneticNorthZVertical,
            toQueue = NSOperationQueue.mainQueue,
            withHandler = { motion, _ ->
                val dm = motion ?: return@startDeviceMotionUpdatesUsingReferenceFrame
                val altitude = -(dm.attitude.pitch * (180.0 / PI))
                val azimuth = (dm.heading + 360.0) % 360.0
                // heading < 0 means compass is unavailable/uncalibrated
                val accuracy = if (dm.heading >= 0.0) 3 else 0
                _orientationData.value = OrientationData(altitude, azimuth, accuracy)
            }
        )
    }

    actual fun stopUpdates() {
        motionManager.stopDeviceMotionUpdates()
    }
}

actual fun computeMagneticDeclination(lat: Double, lon: Double, altMeters: Double): Double = 0.0
