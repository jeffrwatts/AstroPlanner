package com.islandskiesastro.astroplanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun JupiterScreen(
    location: LocationData?,
    hasLocationPermission: Boolean,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    var observingD by remember { mutableStateOf<Double?>(null) }
    var sliderTwilightTimes by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val currentD = observingD ?: AstronomyService.daysFromJ2000()

    LaunchedEffect(location?.latitude, location?.longitude, observingD) {
        sliderTwilightTimes = if (location != null) {
            val d = observingD ?: AstronomyService.daysFromJ2000()
            var times = AstronomyService.getAstronomicalTwilightTimesForD(
                location.latitude, location.longitude, d
            )
            if (observingD == null && times != null && d > times.second) {
                times = AstronomyService.getAstronomicalTwilightTimesForD(
                    location.latitude, location.longitude, d + 1.0
                )
            }
            times
        } else null
    }

    val times = sliderTwilightTimes
    val showSlider = times != null
    val showNow = observingD != null

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Date navigation row ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                observingD = (observingD ?: AstronomyService.daysFromJ2000()) - 1.0
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            Text(
                AstronomyService.dToLocalDateString(currentD, timeZone),
                modifier = Modifier.widthIn(min = 100.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = {
                observingD = (observingD ?: AstronomyService.daysFromJ2000()) + 1.0
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
            AnimatedVisibility(visible = showNow) {
                FilterChip(
                    selected = false,
                    onClick = { observingD = null },
                    label = { Text("Now") }
                )
            }
        }

        // ── Jupiter diagram ───────────────────────────────────────────────────
        JupiterSystemView(
            d = currentD,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        // ── Time slider ───────────────────────────────────────────────────────
        AnimatedVisibility(visible = showSlider) {
            if (times != null) {
                val (eveD, mornD) = times
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Slider(
                        value = ((currentD - eveD) / (mornD - eveD)).toFloat().coerceIn(0f, 1f),
                        onValueChange = { f -> observingD = eveD + f * (mornD - eveD) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        AstronomyService.dToLocalTimeString(currentD, timeZone),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun JupiterSystemView(
    d: Double,
    modifier: Modifier = Modifier
) {
    val state = remember(d) { JupiterMoonCalculator.calculate(d) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val jupRadiusDp = 52.dp
    val moonRadiusDp = 3.dp
    val labelSizeSp = 10.sp
    val legendSizeSp = 11.sp

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val jupR = with(density) { jupRadiusDp.toPx() }
        val moonR = with(density) { moonRadiusDp.toPx() }
        val labelPad = with(density) { 5.dp.toPx() }

        // Scale: px per Jupiter radius, sized so Callisto fits with margin
        // Callisto's max elongation is ~26.4 JR; we leave some margin
        val maxExtent = 28f
        val scale = (minOf(w, h) * 0.42f) / maxExtent

        // Background
        drawRect(color = Color(0xFF0A0A1A), topLeft = Offset(0f, 0f), size = Size(w, h))

        // Jupiter disc
        drawCircle(
            color = Color(0xFFD4A96A),
            radius = jupR,
            center = Offset(cx, cy)
        )

        // Equatorial band (darker rectangle across the disc)
        val bandHeight = jupR * 0.30f
        drawRect(
            color = Color(0xFFB8874A),
            topLeft = Offset(cx - jupR, cy - bandHeight / 2f),
            size = Size(jupR * 2f, bandHeight)
        )

        // GRS oval (when visible)
        if (state.grsVisible) {
            val grsX = cx - jupR * sin(state.grsCmDiff * PI / 180.0).toFloat()
            val grsY = cy + jupR * 0.39f
            val grsW = jupR * 0.45f
            val grsH = jupR * 0.25f
            drawOval(
                color = Color(0xFFCC4400),
                topLeft = Offset(grsX - grsW / 2f, grsY - grsH / 2f),
                size = Size(grsW, grsH),
                style = Fill
            )
        }

        // Moon dots and labels
        val labelStyle = TextStyle(fontSize = labelSizeSp, color = Color.White)
        for (moon in state.moons) {
            val mx = cx + (moon.xJR * scale).toFloat()
            val my = cy + (moon.yJR * scale).toFloat()

            drawCircle(
                color = Color.White,
                radius = moonR,
                center = Offset(mx, my)
            )

            val measured = textMeasurer.measure(moon.name, labelStyle)
            val lx = mx - measured.size.width / 2f
            val ly = my + moonR + labelPad
            drawText(measured, topLeft = Offset(lx, ly))
        }

        // E / W legend (north-up sky-chart convention: East is LEFT, West is RIGHT)
        val legendStyle = TextStyle(fontSize = legendSizeSp, color = Color.White.copy(alpha = 0.6f))
        val eMeasured = textMeasurer.measure("E", legendStyle)
        val wMeasured = textMeasurer.measure("W", legendStyle)
        val legendY = cy - eMeasured.size.height / 2f
        drawText(eMeasured, topLeft = Offset(8f, legendY))
        drawText(wMeasured, topLeft = Offset(w - wMeasured.size.width - 8f, legendY))
    }
}
