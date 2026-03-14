package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private data class SkyObject(
    val obj: CelestialObject,
    val altitude: Double,
    val azimuth: Double,
    val transit: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyPlannerScreen(
    location: LocationData?,
    hasLocationPermission: Boolean,
    repository: CelestialObjectRepository
) {
    var showRecommendedOnly by remember { mutableStateOf(true) }
    var skyObjects by remember { mutableStateOf<List<SkyObject>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadObjects() {
        val objects = if (showRecommendedOnly) repository.getRecommendedObjects()
                      else repository.getAllObjects()
        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        val alt = location?.altitude ?: 0.0

        skyObjects = objects.map { obj ->
            val (ra, dec) = if (obj.type == ObjectType.PLANET) {
                AstronomyService.getPlanetRaDec(obj.objectId)
            } else {
                Pair(obj.ra, obj.dec)
            }
            val altAzm = AstronomyService.getAltAzm(ra, dec, lat, lon, alt)
            val transit = AstronomyService.getMeridianTransit(ra, lon, lat)
            SkyObject(obj.copy(ra = ra, dec = dec), altAzm.altitude, altAzm.azimuth, transit)
        }.sortedByDescending { it.altitude }
    }

    LaunchedEffect(showRecommendedOnly) { loadObjects() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showRecommendedOnly,
                onClick = { showRecommendedOnly = true },
                label = { Text("Recommended") }
            )
            FilterChip(
                selected = !showRecommendedOnly,
                onClick = { showRecommendedOnly = false },
                label = { Text("All") }
            )
        }

        if (skyObjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No objects found. Go to Update to load the catalog.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    loadObjects()
                    isRefreshing = false
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(skyObjects) { skyObj ->
                        SkyObjectItem(skyObj)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyObjectItem(skyObj: SkyObject) {
    val alpha = if (skyObj.altitude < 15.0) 0.4f else 1.0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(skyObj.obj.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            skyObj.obj.objectId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Alt: ${skyObj.altitude.toDegreesMinutes()}  Azm: ${skyObj.azimuth.toDegreesMinutes()}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Transit: ${skyObj.transit}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun Double.toDegreesMinutes(): String {
    val sign = if (this < 0) "-" else ""
    val abs = abs(this)
    val d = abs.toInt()
    val m = (abs - d) * 60.0
    return "$sign$d° ${m.formatFixed(2)}'"
}

private fun Double.formatFixed(decimals: Int): String {
    val factor = pow10(decimals)
    val rounded = (this * factor).toLong().toDouble() / factor
    val str = rounded.toString()
    val dotIdx = str.indexOf('.')
    return if (dotIdx < 0) str + "." + "0".repeat(decimals)
    else {
        val current = str.length - dotIdx - 1
        if (current >= decimals) str.substring(0, dotIdx + decimals + 1)
        else str + "0".repeat(decimals - current)
    }
}

private fun pow10(exp: Int): Double {
    var r = 1.0; repeat(exp) { r *= 10.0 }; return r
}
