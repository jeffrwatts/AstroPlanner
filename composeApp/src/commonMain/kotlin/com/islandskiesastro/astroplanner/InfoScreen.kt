package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun InfoScreen(location: LocationData?, hasLocationPermission: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !hasLocationPermission -> Text("Location permission required")
            location == null -> Text("Acquiring location...")
            else -> {
                Text("Latitude:  ${location.latitude.format(6)}°",  style = MaterialTheme.typography.bodyLarge)
                Text("Longitude: ${location.longitude.format(6)}°", style = MaterialTheme.typography.bodyLarge)
                Text("Altitude:  ${location.altitude.format(1)} m", style = MaterialTheme.typography.bodyLarge)
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

private fun dpow(base: Double, exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= base }
    return result
}
