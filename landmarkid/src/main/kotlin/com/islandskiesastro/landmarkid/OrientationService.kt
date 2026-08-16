package com.islandskiesastro.landmarkid

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI

class OrientationService(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _orientationData = MutableStateFlow(OrientationData(0.0, 0.0, 0))
    val orientationData: StateFlow<OrientationData> = _orientationData.asStateFlow()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            val rotationMatrix = FloatArray(9)
            val adjusted = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, adjusted)
            val angles = FloatArray(3)
            SensorManager.getOrientation(adjusted, angles)
            val altitude = -(angles[1].toDouble() * (180.0 / PI))
            val azimuth = ((angles[0].toDouble() * (180.0 / PI)) + 360.0) % 360.0
            _orientationData.value = OrientationData(altitude, azimuth, event.accuracy)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            _orientationData.value = _orientationData.value.copy(accuracy = accuracy)
        }
    }

    fun startUpdates() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopUpdates() {
        sensorManager.unregisterListener(listener)
    }
}

fun computeMagneticDeclination(lat: Double, lon: Double, altMeters: Double): Double {
    val field = GeomagneticField(lat.toFloat(), lon.toFloat(), altMeters.toFloat(), System.currentTimeMillis())
    return field.declination.toDouble()
}
