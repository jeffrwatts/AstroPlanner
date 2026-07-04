package com.islandskiesastro.landmarkid

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

private val CARDINALS = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")

@Composable
fun CompassAltitudeRuler(heading: Double, altitude: Double, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val tickColor = Color(0xFFFFC107)
    val labelStyle = TextStyle(color = Color.White, fontSize = 14.sp)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Crosshair marking current heading / altitude
        drawLine(tickColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 4f)
        drawLine(tickColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 4f)

        // --- Horizontal compass-heading ruler (±30°, ticks every 5°, labels every 10°) ---
        val pxPerDeg = size.width / 60.0
        val headingStart = heading - 30.0
        var tick = ceil(headingStart / 5.0) * 5.0
        while (tick <= heading + 30.0) {
            val x = (cx + (tick - heading) * pxPerDeg).toFloat()
            val displayHeading = (((tick % 360) + 360) % 360).roundToInt() % 360
            val isMajor = (displayHeading % 10 == 0)
            val tickHeight = if (isMajor) 36f else 18f
            drawLine(tickColor, Offset(x, cy - tickHeight / 2), Offset(x, cy + tickHeight / 2), strokeWidth = 3f)
            if (isMajor) {
                val label = CARDINALS[displayHeading] ?: "$displayHeading°"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(measured, topLeft = Offset(x - measured.size.width / 2f, cy + 22f))
            }
            tick += 5.0
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
