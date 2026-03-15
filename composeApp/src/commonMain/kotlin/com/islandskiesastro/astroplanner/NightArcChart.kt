package com.islandskiesastro.astroplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class NightChartData(
    val twilightEndD: Double,
    val twilightStartD: Double,
    val objectPoints: List<Offset>,   // x = time fraction [0,1], y = alt fraction (0=top=+90°)
    val moonPoints: List<Offset>,
    val hourMarkers: List<Pair<Float, String>>  // x fraction, label e.g. "10pm"
)

// Distinct hardcoded arc colors that read well on the dark (#0A0A1A) background
// regardless of the app's Material theme.
private val ArcColorObject = Color(0xFFFFA040)   // warm amber  — the object you're observing
private val ArcColorMoon   = Color(0xFF90B8E0)   // cool steel-blue — the Moon

@Composable
internal fun NightArcChart(
    skyObj: SkyObject,
    location: LocationData,
    observingD: Double,
    modifier: Modifier = Modifier
) {
    val twilightTimes = remember(location.latitude, location.longitude, observingD) {
        AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, observingD)
    }

    if (twilightTimes == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No astronomical night at this location/date",
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    val chartData = remember(skyObj.obj.objectId, location.latitude, location.longitude, observingD) {
        computeNightChartData(skyObj, location, twilightTimes.first, twilightTimes.second)
    }

    val gridColor  = Color.White.copy(alpha = 0.25f)
    val labelColor = Color.White.copy(alpha = 0.65f)
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val textMeasurer = rememberTextMeasurer()

    // Convert dp padding to pixels here (in composable scope) so Canvas drawing
    // never strays outside its bounds — critical for iOS/Skiko rendering correctness.
    val density = LocalDensity.current
    val leftPadPx   = with(density) { 30.dp.toPx() }
    val bottomPadPx = with(density) { 22.dp.toPx() }
    val strokeHeavy  = with(density) { 1.5f.dp.toPx() }
    val strokeLight  = with(density) { 0.5f.dp.toPx() }
    val strokeObject = with(density) { 2.5f.dp.toPx() }
    val strokeMoon   = with(density) { 1.5f.dp.toPx() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val chartLeft   = leftPadPx
        val chartRight  = w
        val chartTop    = 0f
        val chartBottom = h - bottomPadPx   // x-axis labels drawn inside [chartBottom, h]
        val chartW = chartRight - chartLeft
        val chartH = chartBottom - chartTop

        fun xPx(xFrac: Float) = chartLeft + xFrac * chartW
        fun yPx(altDeg: Double) = (chartTop + ((90.0 - altDeg) / 180.0) * chartH).toFloat()
        fun yPxF(yFrac: Float) = chartTop + yFrac * chartH

        // Background
        drawRect(
            color   = Color(0xFF0A0A1A),
            topLeft = Offset(chartLeft, chartTop),
            size    = Size(chartW, chartH)
        )

        // Horizontal gridlines
        listOf(-60.0, -30.0, 0.0, 30.0, 60.0).forEach { alt ->
            val y = yPx(alt)
            drawLine(
                color       = gridColor,
                start       = Offset(chartLeft, y),
                end         = Offset(chartRight, y),
                strokeWidth = if (alt == 0.0) strokeHeavy else strokeLight
            )
        }

        // Vertical gridlines + x-axis labels (drawn within canvas bounds)
        chartData.hourMarkers.forEach { (xFrac, label) ->
            val x = xPx(xFrac)
            drawLine(
                color       = gridColor.copy(alpha = 0.15f),
                start       = Offset(x, chartTop),
                end         = Offset(x, chartBottom),
                strokeWidth = strokeLight
            )
            val measured = textMeasurer.measure(label, labelStyle)
            // Centre label on the gridline; clamp so it stays within canvas width
            val labelX = (x - measured.size.width / 2f).coerceIn(0f, w - measured.size.width.toFloat())
            // Place label in the reserved bottom padding region, a few px below the chart area
            val labelY = chartBottom + (bottomPadPx - measured.size.height) / 2f
            drawText(
                textMeasurer = textMeasurer,
                text         = label,
                topLeft      = Offset(labelX, labelY),
                style        = labelStyle
            )
        }

        // Y-axis labels (drawn within canvas bounds)
        listOf(-60.0, -30.0, 0.0, 30.0, 60.0).forEach { alt ->
            val y = yPx(alt)
            val label = "${alt.toInt()}°"
            val measured = textMeasurer.measure(label, labelStyle)
            val labelY = (y - measured.size.height / 2f).coerceIn(0f, h - measured.size.height.toFloat())
            drawText(
                textMeasurer = textMeasurer,
                text         = label,
                topLeft      = Offset(0f, labelY),
                style        = labelStyle
            )
        }

        // Moon arc — drawn first so object arc renders on top when they overlap
        if (chartData.moonPoints.size >= 2) {
            val path = Path()
            val first = chartData.moonPoints.first()
            path.moveTo(xPx(first.x), yPxF(first.y))
            chartData.moonPoints.drop(1).forEach { pt ->
                path.lineTo(xPx(pt.x), yPxF(pt.y))
            }
            drawPath(path, ArcColorMoon, style = Stroke(width = strokeMoon))
        }

        // Object arc
        if (chartData.objectPoints.size >= 2) {
            val path = Path()
            val first = chartData.objectPoints.first()
            path.moveTo(xPx(first.x), yPxF(first.y))
            chartData.objectPoints.drop(1).forEach { pt ->
                path.lineTo(xPx(pt.x), yPxF(pt.y))
            }
            drawPath(path, ArcColorObject, style = Stroke(width = strokeObject))
        }
    }
}

private fun computeNightChartData(
    skyObj: SkyObject,
    location: LocationData,
    twilightEndD: Double,
    twilightStartD: Double
): NightChartData {
    val lat = location.latitude
    val lon = location.longitude
    val nightDuration = twilightStartD - twilightEndD
    val stepD = 15.0 / (24.0 * 60.0)  // 15-minute samples

    val objectPoints = mutableListOf<Offset>()
    val moonPoints   = mutableListOf<Offset>()

    var d = twilightEndD
    while (true) {
        val clampedD = d.coerceAtMost(twilightStartD)
        val xFrac = ((clampedD - twilightEndD) / nightDuration).toFloat()

        val (objRa, objDec) = when {
            skyObj.obj.objectId.lowercase() == "moon" -> AstronomyService.moonRaDecForD(clampedD)
            skyObj.obj.type == ObjectType.PLANET       -> AstronomyService.getPlanetRaDecForD(skyObj.obj.objectId, clampedD)
            else                                       -> Pair(skyObj.obj.ra, skyObj.obj.dec)
        }
        val objAlt = AstronomyService.getAltAzmForD(objRa, objDec, lat, lon, clampedD).altitude
        objectPoints.add(Offset(xFrac, ((90.0 - objAlt) / 180.0).toFloat()))

        val (moonRa, moonDec) = AstronomyService.moonRaDecForD(clampedD)
        val moonAlt = AstronomyService.getAltAzmForD(moonRa, moonDec, lat, lon, clampedD).altitude
        moonPoints.add(Offset(xFrac, ((90.0 - moonAlt) / 180.0).toFloat()))

        if (clampedD >= twilightStartD) break
        d += stepD
    }

    // Hour markers: scan at 1-minute UTC boundaries across the night window.
    // The twilight times come from a binary search and are not on minute boundaries,
    // so we floor startMs to the nearest UTC minute before scanning; otherwise the
    // scan never lands on ldt.minute == 0 and no markers are produced.
    val startMs = dToEpochMs(twilightEndD)
    val endMs   = dToEpochMs(twilightStartD)
    val totalMs = (endMs - startMs).toFloat()
    val tz = TimeZone.currentSystemDefault()
    val hourMarkers = mutableListOf<Pair<Float, String>>()
    val seen = mutableSetOf<Int>()
    // Floor to the nearest UTC minute so every minute==0 boundary is guaranteed to be visited.
    var scanMs = (startMs / 60_000L) * 60_000L
    while (scanMs <= endMs) {
        if (scanMs >= startMs) {
            val ldt = Instant.fromEpochMilliseconds(scanMs).toLocalDateTime(tz)
            if (ldt.hour % 2 == 0 && ldt.minute == 0 && ldt.hour !in seen) {
                val xFrac = (scanMs - startMs).toFloat() / totalMs
                hourMarkers.add(Pair(xFrac, formatHourLabel(ldt.hour)))
                seen.add(ldt.hour)
            }
        }
        scanMs += 60_000L
    }

    return NightChartData(twilightEndD, twilightStartD, objectPoints, moonPoints, hourMarkers)
}

/** Convert days-from-J2000 to Unix epoch milliseconds. */
private fun dToEpochMs(d: Double): Long = ((d + 10957.5) * 86_400_000.0).toLong()

private fun formatHourLabel(hour: Int): String = when {
    hour == 0  -> "12am"
    hour == 12 -> "12pm"
    hour < 12  -> "${hour}am"
    else       -> "${hour - 12}pm"
}
