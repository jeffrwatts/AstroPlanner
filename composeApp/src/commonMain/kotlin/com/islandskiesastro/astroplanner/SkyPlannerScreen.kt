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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

private data class MoonInfo(
    val pct: Int,
    val riseD: Double?,
    val setD: Double?,
    val upAtDusk: Boolean,
    val isWaxing: Boolean
)

private enum class ObjectFilter { RECOMMENDED, ALL, VARIABLES }

internal data class SkyObject(
    val obj: CelestialObject,
    val altitude: Double,
    val azimuth: Double,
    val transit: String,
    val isRising: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyPlannerScreen(
    location: LocationData?,
    hasLocationPermission: Boolean,
    repository: CelestialObjectRepository,
    equipmentRepository: EquipmentRepository,
    planRepository: PlanRepository? = null,
    locationName: String = "GPS Location",
    onBackActionChanged: ((() -> Unit)?) -> Unit = {},
    onTitleChanged: (String?) -> Unit = {},
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    var objectFilter by remember { mutableStateOf(ObjectFilter.RECOMMENDED) }
    var searchQuery by remember { mutableStateOf("") }
    var skyObjects by remember { mutableStateOf<List<SkyObject>>(emptyList()) }
    var imagesMap by remember { mutableStateOf<Map<String, CelestialObjectImage>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    var selectedObject by remember { mutableStateOf<Pair<SkyObject, CelestialObjectImage?>?>(null) }
    val listState = rememberLazyListState()
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
            val upAtDusk   = AstronomyService.getAltAzmForD(ra, dec, loc.latitude, loc.longitude, eveD).altitude > 0.0
            val illumNow   = AstronomyService.getMoonIllumination(eveD)
            val illumNext  = AstronomyService.getMoonIllumination(eveD + 1.0 / 48.0)
            MoonInfo(
                pct      = (illumNow * 100).roundToInt(),
                riseD    = riseSet.first,
                setD     = riseSet.second,
                upAtDusk = upAtDusk,
                isWaxing = illumNext >= illumNow
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
        if (location != null) {
            val d = observingD ?: AstronomyService.daysFromJ2000()
            // If already within the current window, skip recomputation. Recomputing with d near
            // the boundary can produce a slightly different tMorn due to bisection rounding,
            // making d > times.second true and falsely advancing to the next night.
            val current = sliderTwilightTimes
            if (current != null && d >= current.first && d <= current.second) return@LaunchedEffect

            var times = AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, d)
            // If d is already past morning twilight (daytime), advance to find the next night.
            if (times != null && d > times.second) {
                times = AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, d + 1.0)
            }
            // If the user navigated to a specific day and the time falls before the start of night, snap to start of night.
            if (times != null && observingD != null && observingD!! < times.first) {
                observingD = times.first
            }
            sliderTwilightTimes = times
        } else {
            sliderTwilightTimes = null
        }
    }

    fun loadObjects() {
        val objects = when (objectFilter) {
            ObjectFilter.RECOMMENDED -> repository.getRecommendedObjects()
            ObjectFilter.ALL         -> repository.getAllObjects()
            ObjectFilter.VARIABLES   -> repository.getAllObjects().filter { it.type == ObjectType.VARIABLE_STAR || it.type == ObjectType.STANDARD_FIELD }
        }
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
            val altNext = AstronomyService.getAltAzmForD(ra, dec, lat, lon, d + 1.0 / 1440.0).altitude
            val transit = AstronomyService.getMeridianTransitForD(ra, lon, lat, d, timeZone)
            SkyObject(obj.copy(ra = ra, dec = dec), altAzm.altitude, altAzm.azimuth, transit, altNext > altAzm.altitude)
        }.sortedByDescending { it.altitude }

        imagesMap = repository.getImagesMap()
    }

    LaunchedEffect(objectFilter, observingD) { loadObjects() }

    if (selectedObject != null) {
        if (selectedObject!!.first.obj.type == ObjectType.VARIABLE_STAR || selectedObject!!.first.obj.type == ObjectType.STANDARD_FIELD) {
            VariableStarDetailScreen(
                skyObj                    = selectedObject!!.first,
                location                  = location,
                observingD                = currentD,
                equipmentRepository       = equipmentRepository,
                celestialObjectRepository = repository,
                onBack                    = { selectedObject = null },
                onBackActionChanged       = onBackActionChanged,
                timeZone                  = timeZone
            )
            return
        }
        DetailScreen(
            skyObj                    = selectedObject!!.first,
            image                     = selectedObject!!.second,
            location                  = location,
            observingD                = currentD,
            equipmentRepository       = equipmentRepository,
            planRepository            = planRepository,
            celestialObjectRepository = repository,
            locationName              = locationName,
            onBack                    = { selectedObject = null },
            onBackActionChanged       = onBackActionChanged,
            onDeleted                 = { selectedObject = null; loadObjects() },
            onEdited                  = {
                loadObjects()
                val editedId = selectedObject?.first?.obj?.id
                if (editedId != null) {
                    val updated = skyObjects.find { it.obj.id == editedId }
                    selectedObject = updated?.let { Pair(it, imagesMap[it.obj.objectId]) }
                }
            },
            timeZone                  = timeZone
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Session card ──────────────────────────────────────────────────────
        val times       = sliderTwilightTimes
        val isNighttime = times != null && currentD >= times.first && currentD <= times.second
        val showSlider  = times != null && (observingD != null || isNighttime)
        val showTonight = times != null && observingD == null && !isNighttime
        val showNow     = observingD != null

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 4.dp),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

                // Row A: date nav + Tonight button (daytime only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        observingD = (observingD ?: AstronomyService.daysFromJ2000()) - 1.0
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Text(
                        AstronomyService.dToLocalDateString(currentD, timeZone),
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style     = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        observingD = (observingD ?: AstronomyService.daysFromJ2000()) + 1.0
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                    val isLive = observingD == null && isNighttime
                    FilterChip(
                        selected = isLive,
                        onClick  = {
                            when {
                                showTonight && times != null -> observingD = times.first
                                showNow                      -> observingD = null
                                else                         -> { }
                            }
                        },
                        label = {
                            Text(
                                when { showTonight -> "Tonight"; showNow -> "Now"; else -> "Live" },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }

                // Row B: moon phase icon + pct + rise/set grouped together
                if (moonInfo != null) {
                    val riseStr    = moonInfo.riseD?.let { "↑ ${AstronomyService.dToLocalTimeString(it, timeZone)}" }
                    val setStr     = moonInfo.setD?.let  { "↓ ${AstronomyService.dToLocalTimeString(it, timeZone)}" }
                    val riseSetStr = when {
                        riseStr != null && setStr != null -> "$riseStr  ·  $setStr"
                        riseStr != null                   -> riseStr
                        setStr  != null                   -> setStr
                        moonInfo.upAtDusk                 -> "above horizon all night"
                        else                              -> "below horizon"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MoonPhaseIcon(
                            illumination = moonInfo.pct / 100f,
                            isWaxing     = moonInfo.isWaxing,
                            modifier     = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${moonInfo.pct}%  ·  $riseSetStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Row C: labeled time slider (nighttime only)
                AnimatedVisibility(visible = showSlider) {
                    if (times != null) {
                        val (eveD, mornD) = times
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                AstronomyService.dToLocalTimeString(eveD, timeZone).compactTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Slider(
                                value = ((currentD - eveD) / (mornD - eveD)).toFloat().coerceIn(0f, 1f),
                                onValueChange = { f -> observingD = (eveD + f * (mornD - eveD)).coerceIn(eveD, mornD) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                AstronomyService.dToLocalTimeString(mornD, timeZone).compactTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                AstronomyService.dToLocalTimeString(currentD, timeZone),
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.widthIn(min = 64.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search by name or ID") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true
        )

        // ── Filter chips ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = objectFilter == ObjectFilter.RECOMMENDED,
                onClick  = { objectFilter = ObjectFilter.RECOMMENDED },
                label    = { Text("Recommended") }
            )
            FilterChip(
                selected = objectFilter == ObjectFilter.ALL,
                onClick  = { objectFilter = ObjectFilter.ALL },
                label    = { Text("All") }
            )
            FilterChip(
                selected = objectFilter == ObjectFilter.VARIABLES,
                onClick  = { objectFilter = ObjectFilter.VARIABLES },
                label    = { Text("Variables") }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )

        val filteredObjects = remember(skyObjects, searchQuery) {
            if (searchQuery.isBlank()) skyObjects
            else {
                val q = searchQuery.trim().lowercase()
                skyObjects.filter { it.obj.objectId.lowercase().contains(q) || it.obj.displayName.lowercase().contains(q) }
            }
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
                    refreshScope.launch {
                        isRefreshing = true
                        delay(50) // allow a frame to render with isRefreshing=true so PullToRefreshBox state machine reaches Refreshing before endRefresh() is called
                        loadObjects()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filteredObjects) { skyObj ->
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
            Text(skyObj.obj.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                skyObj.obj.objectId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (skyObj.isRising) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = if (skyObj.isRising) "Rising" else "Setting",
                    tint = if (skyObj.isRising) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Alt: ${skyObj.altitude.toDegreesMinutes()}  Azm: ${skyObj.azimuth.toDegreesMinutes()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Transit: ${skyObj.transit}",
                style = MaterialTheme.typography.bodyMedium
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

private fun String.compactTime() = replace(" pm", "p").replace(" am", "a")

@Composable
private fun MoonPhaseIcon(illumination: Float, isWaxing: Boolean, modifier: Modifier = Modifier) {
    val litColor  = Color(0xFFF0DFA0)
    val darkColor = Color(0xFF1C2840)
    val rimColor  = Color(0xFF4A5870)

    Canvas(modifier = modifier) {
        val r  = size.minDimension / 2f
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val f  = illumination.coerceIn(0f, 1f)

        drawCircle(color = darkColor, radius = r, center = Offset(cx, cy))
        drawCircle(color = rimColor, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.5f))

        if (f > 0.02f) {
            val xr       = r * kotlin.math.abs(2f * f - 1f)
            val fullRect = Rect(cx - r, cy - r, cx + r, cy + r)
            val termRect = Rect(cx - xr, cy - r, cx + xr, cy + r)
            val mainSweep = if (isWaxing) 180f else -180f
            val termSweep = if (isWaxing == (f < 0.5f)) -180f else 180f

            val litPath = Path()
            litPath.moveTo(cx, cy - r)
            litPath.arcTo(fullRect, startAngleDegrees = -90f, sweepAngleDegrees = mainSweep, forceMoveTo = false)
            litPath.arcTo(termRect, startAngleDegrees =  90f, sweepAngleDegrees = termSweep, forceMoveTo = false)
            litPath.close()
            drawPath(litPath, litColor)
        }
    }
}

