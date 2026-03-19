package com.islandskiesastro.astroplanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

private data class MoonInfo(
    val pct: Int,
    val riseD: Double?,
    val setD: Double?,
    val upAtDusk: Boolean
)

internal data class SkyObject(
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
    repository: CelestialObjectRepository,
    onBackActionChanged: ((() -> Unit)?) -> Unit = {},
    onTitleChanged: (String?) -> Unit = {},
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    var showRecommendedOnly by remember { mutableStateOf(true) }
    var skyObjects by remember { mutableStateOf<List<SkyObject>>(emptyList()) }
    var imagesMap by remember { mutableStateOf<Map<String, CelestialObjectImage>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedObject by remember { mutableStateOf<Pair<SkyObject, CelestialObjectImage?>?>(null) }
    var observingD by remember { mutableStateOf<Double?>(null) }
    var sliderTwilightTimes by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val currentD = observingD ?: AstronomyService.daysFromJ2000()

    val moonInfo: MoonInfo? = remember(location?.latitude, location?.longitude, sliderTwilightTimes) {
        val loc = location
        val tw  = sliderTwilightTimes
        if (loc != null && tw != null) {
            val (eveD, mornD) = tw
            val riseSet = AstronomyService.getMoonRiseSetTimes(loc.latitude, loc.longitude, eveD, mornD)
            val (ra, dec) = AstronomyService.moonRaDecForD(eveD)
            val upAtDusk = AstronomyService.getAltAzmForD(ra, dec, loc.latitude, loc.longitude, eveD).altitude > 0.0
            MoonInfo(
                pct      = (AstronomyService.getMoonIllumination(eveD) * 100).roundToInt(),
                riseD    = riseSet.first,
                setD     = riseSet.second,
                upAtDusk = upAtDusk
            )
        } else null
    }

    // Notify the parent TopAppBar whether a back action is available and what title to show.
    LaunchedEffect(selectedObject) {
        if (selectedObject != null) {
            onBackActionChanged { selectedObject = null }
            onTitleChanged(selectedObject!!.first.obj.displayName)
        } else {
            onBackActionChanged(null)
            onTitleChanged(null)
        }
    }

    LaunchedEffect(location?.latitude, location?.longitude, observingD) {
        sliderTwilightTimes = if (location != null) {
            val d = observingD ?: AstronomyService.daysFromJ2000()
            var times = AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, d)
            // If observingD is unset (live "now") and we're already past morning twilight,
            // we're in daytime — advance by one day to find tonight's night.
            if (observingD == null && times != null && d > times.second) {
                times = AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, d + 1.0)
            }
            times
        } else null
    }

    fun loadObjects() {
        val objects = if (showRecommendedOnly) repository.getRecommendedObjects()
                      else repository.getAllObjects()
        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        val d = observingD ?: AstronomyService.daysFromJ2000()

        skyObjects = objects.map { obj ->
            val (ra, dec) = if (obj.type == ObjectType.PLANET) {
                AstronomyService.getPlanetRaDecForD(obj.objectId, d)
            } else {
                Pair(obj.ra, obj.dec)
            }
            val altAzm = AstronomyService.getAltAzmForD(ra, dec, lat, lon, d)
            val transit = AstronomyService.getMeridianTransitForD(ra, lon, lat, d, timeZone)
            SkyObject(obj.copy(ra = ra, dec = dec), altAzm.altitude, altAzm.azimuth, transit)
        }.sortedByDescending { it.altitude }

        imagesMap = repository.getImagesMap()
    }

    LaunchedEffect(showRecommendedOnly, observingD) { loadObjects() }

    if (selectedObject != null) {
        DetailScreen(
            skyObj              = selectedObject!!.first,
            image               = selectedObject!!.second,
            location            = location,
            observingD          = currentD,
            onBack              = { selectedObject = null },
            onBackActionChanged = onBackActionChanged,
            timeZone            = timeZone
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Objects filter ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Objects",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )

        // ── Observing Time ────────────────────────────────────────────────────
        Text(
            "Observing Time",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 0.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
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
        }

        val times = sliderTwilightTimes
        val isNighttime = times != null && currentD >= times.first && currentD <= times.second
        val showSlider  = times != null && (observingD != null || isNighttime)
        val showTonight = times != null && observingD == null && !isNighttime
        val showNow     = observingD != null

        // Context-sensitive row: "Tonight →" when daytime Now, "Now" when navigated away
        AnimatedVisibility(visible = showTonight || showNow) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (showTonight && times != null) {
                    FilterChip(
                        selected = false,
                        onClick = { observingD = times.first },
                        label = { Text("Tonight \u2192") }
                    )
                } else if (showNow) {
                    FilterChip(
                        selected = false,
                        onClick = { observingD = null },
                        label = { Text("Now") }
                    )
                }
            }
        }

        AnimatedVisibility(visible = showSlider) {
            if (times != null) {
                val (eveD, mornD) = times
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        AnimatedVisibility(visible = moonInfo != null) {
            if (moonInfo != null) {
                val riseText = moonInfo.riseD?.let { "Moonrise: ${AstronomyService.dToLocalTimeString(it, timeZone)}" }
                val setText  = moonInfo.setD?.let  { "Moonset: ${AstronomyService.dToLocalTimeString(it, timeZone)}" }
                val riseSetText = when {
                    riseText != null && setText != null -> "$riseText   $setText"
                    riseText != null                   -> riseText
                    setText  != null                   -> setText
                    moonInfo.upAtDusk                  -> "Moon above horizon all night"
                    else                               -> "Moon below horizon all night"
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Moon: ${moonInfo.pct}% illuminated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        riseSetText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )

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
                        val image = imagesMap[skyObj.obj.objectId]
                        SkyObjectItem(
                            skyObj = skyObj,
                            thumbPath = image?.thumbPath,
                            onClick = {
                                selectedObject = Pair(skyObj, image)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyObjectItem(
    skyObj: SkyObject,
    thumbPath: String?,
    onClick: () -> Unit
) {
    val alpha = if (skyObj.altitude < 15.0) 0.4f else 1.0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumbPath != null) {
            AsyncImage(
                model = thumbPath.toPath(),
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = defaultImagePainter(skyObj.obj.type),
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
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
