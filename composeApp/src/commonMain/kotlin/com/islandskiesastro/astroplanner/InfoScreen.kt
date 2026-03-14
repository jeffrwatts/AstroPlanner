package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

@Composable
fun InfoScreen(location: LocationData?, hasLocationPermission: Boolean) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now()
            delay(1000)
        }
    }

    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val timeStr = "${local.hour.pad2()}:${local.minute.pad2()}:${local.second.pad2()}"
    val dateStr = "${local.monthNumber.pad2()}/${local.dayOfMonth.pad2()}/${local.year}"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Time: $timeStr", style = MaterialTheme.typography.bodyLarge)
        Text("Date: $dateStr", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        when {
            !hasLocationPermission -> Text("Location permission required")
            location == null -> Text("Acquiring location...")
            else -> {
                Text("Latitude:  ${location.latitude.format(6)}°",  style = MaterialTheme.typography.bodyLarge)
                Text("Longitude: ${location.longitude.format(6)}°", style = MaterialTheme.typography.bodyLarge)
                Text("Altitude:  ${location.altitude.format(1)} m", style = MaterialTheme.typography.bodyLarge)
                Text("Accuracy:  ${location.accuracy.format(1)} m", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun Double.format(decimals: Int): String {
    val factor = dpow(10.0, decimals)
    val rounded = (this * factor).roundToInt().toDouble() / factor
    val str = rounded.toString()
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
