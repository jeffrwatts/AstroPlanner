package com.islandskiesastro.astroplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun AltAzmScreen(
    orientationService: OrientationService,
    location: LocationData?,
    hasLocationPermission: Boolean
) {
    DisposableEffect(Unit) {
        orientationService.startUpdates()
        onDispose { orientationService.stopUpdates() }
    }

    val orientationData by orientationService.orientationData.collectAsState()
    val declination = location?.let {
        computeMagneticDeclination(it.latitude, it.longitude, it.altitude)
    } ?: 0.0

    val trueAzimuth = (orientationData.azimuth + declination + 360.0) % 360.0
    val altitude = orientationData.altitude

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(modifier = Modifier.fillMaxSize())

        val accuracyText = when (orientationData.accuracy) {
            3 -> "High"
            2 -> "Medium"
            1 -> "Low"
            else -> "Unreliable"
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Row {
                ReadoutItem("Alt", "${altitude.fmt1()}°")
                ReadoutItem("Az", "${trueAzimuth.fmt1()}°")
                ReadoutItem("Accuracy", accuracyText)
            }
        }

        AltAzmRuler(
            altitude = altitude,
            azimuth = trueAzimuth,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ReadoutItem(label: String, value: String) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 18.sp)
    }
}

// KMP-safe 1-decimal-place formatter (avoids String.format which is JVM-only)
private fun Double.fmt1(): String {
    val rounded = (this * 10.0).roundToInt()
    val sign = if (rounded < 0) "-" else ""
    val absVal = abs(rounded)
    return "$sign${absVal / 10}.${absVal % 10}"
}

@Composable
private fun AltAzmRuler(altitude: Double, azimuth: Double, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val tickColor = Color.Red
    val labelStyle = TextStyle(color = Color.White, fontSize = 14.sp)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Crosshair center lines
        drawLine(tickColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 4f)
        drawLine(tickColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 4f)

        // --- Horizontal azimuth ruler (±30°, ticks every 5°, labels every 10°) ---
        val azPxPerDeg = size.width / 60.0
        val azStart = azimuth - 30.0
        var tickAz = ceil(azStart / 5.0) * 5.0
        while (tickAz <= azimuth + 30.0) {
            val x = (cx + (tickAz - azimuth) * azPxPerDeg).toFloat()
            val isMajor = (tickAz.roundToInt() % 10 == 0)
            val tickHeight = if (isMajor) 36f else 18f
            drawLine(tickColor, Offset(x, cy - tickHeight / 2), Offset(x, cy + tickHeight / 2), strokeWidth = 3f)
            if (isMajor) {
                val displayAz = ((tickAz % 360) + 360) % 360
                val label = "${displayAz.roundToInt()}°"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(measured, topLeft = Offset(x - measured.size.width / 2f, cy + 22f))
            }
            tickAz += 5.0
        }

        // --- Vertical altitude ruler (±45°, ticks every 5°, labels every 15°) ---
        val altPxPerDeg = size.height / 90.0
        val altStart = altitude - 45.0
        var tickAlt = ceil(altStart / 5.0) * 5.0
        while (tickAlt <= altitude + 45.0) {
            if (tickAlt < -90 || tickAlt > 90) { tickAlt += 5.0; continue }
            val y = (cy - (tickAlt - altitude) * altPxPerDeg).toFloat()
            val isMajor = (tickAlt.roundToInt() % 15 == 0)
            val tickWidth = if (isMajor) 36f else 18f
            drawLine(tickColor, Offset(cx - tickWidth / 2, y), Offset(cx + tickWidth / 2, y), strokeWidth = 3f)
            if (isMajor) {
                val label = "${tickAlt.roundToInt()}°"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(measured, topLeft = Offset(cx + 22f, y - measured.size.height / 2f))
            }
            tickAlt += 5.0
        }
    }
}
