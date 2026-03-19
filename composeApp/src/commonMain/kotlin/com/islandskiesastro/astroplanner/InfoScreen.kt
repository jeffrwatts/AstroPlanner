package com.islandskiesastro.astroplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun InfoScreen(location: LocationData?, hasLocationPermission: Boolean, timeZone: TimeZone = TimeZone.currentSystemDefault()) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now()
            delay(1000)
        }
    }

    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical   by remember { mutableStateOf(false) }

    val local   = now.toLocalDateTime(timeZone)
    val timeStr = "${local.hour.pad2()}:${local.minute.pad2()}:${local.second.pad2()}"
    val dateStr = "${local.monthNumber.pad2()}/${local.dayOfMonth.pad2()}/${local.year}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Time: $timeStr", style = MaterialTheme.typography.bodyLarge)
        Text("Date: $dateStr", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        when {
            !hasLocationPermission -> Text("Location permission required")
            location == null       -> Text("Acquiring location...")
            else -> {
                Text("Latitude:  ${location.latitude.toDms(isLat = true)}",  style = MaterialTheme.typography.bodyLarge)
                Text("Longitude: ${location.longitude.toDms(isLat = false)}", style = MaterialTheme.typography.bodyLarge)
                Text("Altitude:  ${location.altitude.format(1)} m",          style = MaterialTheme.typography.bodyLarge)
                if (location.accuracy != 0.0) {
                    Text("Accuracy:  ${location.accuracy.format(1)} m",       style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(Modifier.height(20.dp))

                val lha = remember(now, location.longitude) {
                    AstronomyService.getPolarisHourAngle(
                        location.longitude,
                        now.toEpochMilliseconds()
                    )
                }

                Text(
                    "CNP Clock",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PolarisChart(
                    hourAngleDeg   = lha,
                    flipHorizontal = flipHorizontal,
                    flipVertical   = flipVertical,
                    modifier       = Modifier.size(240.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Flip View",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Horizontal", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = flipHorizontal, onCheckedChange = { flipHorizontal = it })
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Vertical", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = flipVertical, onCheckedChange = { flipVertical = it })
                }
            }
        }
    }
}

@Composable
private fun PolarisChart(
    hourAngleDeg: Double,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    modifier: Modifier = Modifier
) {
    // Screen position of Polaris on the polar circle.
    // LHA = 0 → top (upper culmination); motion is counterclockwise as LHA increases.
    val lhaRad = hourAngleDeg * PI / 180.0
    var starX = -sin(lhaRad).toFloat()
    var starY = -cos(lhaRad).toFloat()
    if (flipHorizontal) starX = -starX
    if (flipVertical)   starY = -starY

    val density = LocalDensity.current
    val strokeOrbit   = with(density) { 1.5f.dp.toPx() }
    val strokeTick    = with(density) { 1.0f.dp.toPx() }
    val strokeMajor   = with(density) { 2.0f.dp.toPx() }
    val strokeLine    = with(density) { 1.0f.dp.toPx() }
    val radiusCnp     = with(density) { 3.dp.toPx() }
    val radiusPolaris = with(density) { 6.dp.toPx() }

    Canvas(modifier = modifier) {
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val r  = minOf(cx, cy) * 0.72f

        // Orbit circle
        drawCircle(
            color  = Color.White.copy(alpha = 0.25f),
            radius = r,
            center = Offset(cx, cy),
            style  = Stroke(width = strokeOrbit)
        )

        // Tick marks at 15° intervals clockwise from top (24-tick clock face)
        for (i in 0 until 24) {
            val angle   = i * 15.0 * PI / 180.0
            val sa      = sin(angle).toFloat()
            val ca      = cos(angle).toFloat()
            val isMajor = i % 6 == 0          // cardinal positions (12, 3, 6, 9)
            val innerR  = if (isMajor) r * 0.82f else r * 0.88f
            drawLine(
                color       = Color.White.copy(alpha = if (isMajor) 0.55f else 0.25f),
                start       = Offset(cx + sa * innerR, cy - ca * innerR),
                end         = Offset(cx + sa * r,      cy - ca * r),
                strokeWidth = if (isMajor) strokeMajor else strokeTick
            )
        }

        // Thin line from CNP to Polaris
        drawLine(
            color       = Color.White.copy(alpha = 0.20f),
            start       = Offset(cx, cy),
            end         = Offset(cx + starX * r, cy + starY * r),
            strokeWidth = strokeLine
        )

        // CNP center dot
        drawCircle(
            color  = Color.White.copy(alpha = 0.85f),
            radius = radiusCnp,
            center = Offset(cx, cy)
        )

        // Polaris — amber dot matching NightArcChart's object color
        drawCircle(
            color  = Color(0xFFFFA040),
            radius = radiusPolaris,
            center = Offset(cx + starX * r, cy + starY * r)
        )
    }
}

private fun Double.toDms(isLat: Boolean): String {
    val abs     = kotlin.math.abs(this)
    val deg     = abs.toInt()
    val minFull = (abs - deg) * 60.0
    val min     = minFull.toInt()
    val sec     = (minFull - min) * 60.0
    val dir     = if (isLat) (if (this >= 0) "N" else "S")
                  else       (if (this >= 0) "E" else "W")
    val secStr  = sec.format(2).padStart(5, '0')
    return "${deg}° ${min.toString().padStart(2, '0')}' ${secStr}\" $dir"
}

private fun Double.format(decimals: Int): String {
    val factor  = dpow(10.0, decimals)
    val rounded = (this * factor).roundToInt().toDouble() / factor
    val str     = rounded.toString()
    val dotIndex = str.indexOf('.')
    return if (dotIndex < 0) str + "." + "0".repeat(decimals)
    else {
        val current = str.length - dotIndex - 1
        if (current >= decimals) str.substring(0, dotIndex + decimals + 1)
        else str + "0".repeat(decimals - current)
    }
}

private fun Int.pad2(): String = toString().padStart(2, '0')

private fun dpow(base: Double, exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= base }
    return result
}
