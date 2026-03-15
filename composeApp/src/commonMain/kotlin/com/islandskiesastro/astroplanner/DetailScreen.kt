package com.islandskiesastro.astroplanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun DetailScreen(
    skyObj: SkyObject,
    image: CelestialObjectImage?,
    location: LocationData?,
    observingD: Double,
    onBack: () -> Unit = {},
    onBackActionChanged: ((() -> Unit)?) -> Unit = {}
) {
    val twilightTimes = remember(location?.latitude, location?.longitude, observingD) {
        if (location != null)
            AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, observingD)
        else null
    }

    var showFov by remember { mutableStateOf(false) }

    LaunchedEffect(showFov) {
        onBackActionChanged(if (showFov) ({ showFov = false }) else onBack)
    }

    if (showFov) {
        FieldOfViewScreen(skyObj = skyObj, onBack = { showFov = false })
        return
    }

    val moonNightInfo = remember(location?.latitude, location?.longitude, observingD) {
        if (location != null && twilightTimes != null) {
            val (eveD, mornD) = twilightTimes
            val riseSet = AstronomyService.getMoonRiseSetTimes(
                location.latitude, location.longitude, eveD, mornD
            )
            val (ra, dec) = AstronomyService.moonRaDecForD(eveD)
            val altAtDusk = AstronomyService.getAltAzmForD(
                ra, dec, location.latitude, location.longitude, eveD
            ).altitude
            Triple(riseSet.first, riseSet.second, altAtDusk > 0.0)
        } else Triple(null, null, false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (image?.fullPath != null) {
            AsyncImage(
                model = image.fullPath.toPath(),
                contentDescription = skyObj.obj.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = defaultImagePainter(skyObj.obj.type),
                    contentDescription = skyObj.obj.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "RA: ${skyObj.obj.ra.formatRa()}   Dec: ${skyObj.obj.dec.formatDec()}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(2.dp))
            val typeStr = skyObj.obj.type.name.lowercase()
                .replaceFirstChar { it.uppercase() } +
                (skyObj.obj.subType?.let { " / $it" } ?: "")
            Text(typeStr, style = MaterialTheme.typography.bodyMedium)
            if (!skyObj.obj.constellation.isNullOrBlank()) {
                Text(
                    "Constellation: ${skyObj.obj.constellation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showFov = true }) {
                Text("Field Of View")
            }

            if (location != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Visibility Tonight \u2014 ${AstronomyService.dToLocalDateString(observingD)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))

                if (twilightTimes != null) {
                    val (eveD, mornD) = twilightTimes
                    val (moonRiseD, moonSetD, moonUpAtDusk) = moonNightInfo
                    val moonPct = (AstronomyService.getMoonIllumination(eveD) * 100).roundToInt()

                    Text(
                        "Astro night:  ${AstronomyService.dToLocalTimeString(eveD)} – ${AstronomyService.dToLocalTimeString(mornD)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Moon: $moonPct% illuminated",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val moonRiseText = moonRiseD?.let {
                        "Moonrise: ${AstronomyService.dToLocalTimeString(it)}"
                    }
                    val moonSetText = moonSetD?.let {
                        "Moonset: ${AstronomyService.dToLocalTimeString(it)}"
                    }
                    when {
                        moonRiseText != null && moonSetText != null ->
                            Text(
                                "$moonRiseText   $moonSetText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        moonRiseText != null ->
                            Text(
                                moonRiseText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        moonSetText != null ->
                            Text(
                                moonSetText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        moonUpAtDusk ->
                            Text(
                                "Moon above horizon all night",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        else ->
                            Text(
                                "Moon below horizon all night",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    }
                } else {
                    Text(
                        "No astronomical night at this location/date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))
                NightArcChart(
                    skyObj     = skyObj,
                    location   = location,
                    observingD = observingD,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun Double.formatRa(): String {
    val hours = this / 15.0
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    val s = (hours - h - m / 60.0) * 3600
    return "${h}h ${m}m ${s.toInt()}s"
}

private fun Double.formatDec(): String {
    val sign = if (this < 0) "-" else "+"
    val a = abs(this)
    val d = a.toInt()
    val m = ((a - d) * 60).toInt()
    val s = (a - d - m / 60.0) * 3600
    return "$sign${d}° ${m}' ${s.toInt()}\""
}
