package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone

private const val THIRTY_MIN_D = 30.0 / 1440.0

@Composable
internal fun VariableStarDetailScreen(
    skyObj: SkyObject,
    location: LocationData?,
    observingD: Double,
    equipmentRepository: EquipmentRepository,
    celestialObjectRepository: CelestialObjectRepository,
    onBack: () -> Unit = {},
    onBackActionChanged: ((() -> Unit)?) -> Unit = {},
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    var showFov by remember { mutableStateOf(false) }
    var showCompStars by remember { mutableStateOf(false) }
    var isLocal by remember { mutableStateOf(true) }
    var comparisonStars by remember { mutableStateOf<List<ComparisonStar>>(emptyList()) }

    LaunchedEffect(skyObj.obj.objectId) {
        comparisonStars = celestialObjectRepository.getComparisonStars(skyObj.obj.objectId)
    }

    LaunchedEffect(showFov) {
        onBackActionChanged(if (showFov) ({ showFov = false }) else onBack)
    }

    if (showFov) {
        FieldOfViewScreen(
            skyObj = skyObj,
            equipmentRepository = equipmentRepository,
            comparisonStars = comparisonStars,
            onBack = { showFov = false }
        )
        return
    }

    val obj = skyObj.obj
    val twilightTimes = remember(location?.latitude, location?.longitude, observingD) {
        if (location != null)
            AstronomyService.getAstronomicalTwilightTimesForD(location.latitude, location.longitude, observingD)
        else null
    }

    val windowStartD = twilightTimes?.let { it.first - THIRTY_MIN_D }
    val windowEndD = twilightTimes?.let { it.second + THIRTY_MIN_D }

    val events = remember(obj.variablePeriodDays, obj.variableEpochJd, windowStartD, windowEndD) {
        val period = obj.variablePeriodDays
        val epoch = obj.variableEpochJd
        if (period != null && epoch != null && windowStartD != null && windowEndD != null) {
            AstronomyService.predictVariableEventsForD(period, epoch, windowStartD, windowEndD)
        } else emptyList()
    }

    val eventLabel = if (obj.variableEpochType == "MIN") "Minimum" else "Maximum"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "RA: ${obj.ra.formatRa()}   Dec: ${obj.dec.formatDec()}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(2.dp))
        obj.magnitude?.let { mag ->
            Text("Magnitude: $mag", style = MaterialTheme.typography.bodyMedium)
        }
        if (!obj.subType.isNullOrBlank()) {
            Text("Variable Type: ${obj.subType}", style = MaterialTheme.typography.bodyMedium)
        }
        obj.variablePeriodDays?.let { period ->
            val periodText = if (period < 1.0) {
                val totalMinutes = (period * 24 * 60).toInt()
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                "$period d (${h}h ${m}m)"
            } else {
                "$period d"
            }
            Text("Period: $periodText", style = MaterialTheme.typography.bodyMedium)
        }
        if (!obj.constellation.isNullOrBlank()) {
            Text(
                "Constellation: ${obj.constellation}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showFov = true }) {
                Text("Field Of View")
            }
            OutlinedButton(onClick = { showCompStars = true }) {
                Text("Comparison Stars")
            }
        }

        if (showCompStars) {
            AlertDialog(
                onDismissRequest = { showCompStars = false },
                title = { Text("Comparison Stars") },
                text = {
                    if (comparisonStars.isEmpty()) {
                        Text(
                            "No comparison star data available for this target.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(comparisonStars.sortedBy { it.label }) { comp ->
                                Text(
                                    "Label ${comp.label}   AUID: ${comp.auid}   ${comp.mag?.let { "Mag: $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "RA: ${comp.ra.formatRa()}   Dec: ${comp.dec.formatDec()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCompStars = false }) { Text("Close") }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isLocal, onClick = { isLocal = true }, label = { Text("Local") })
            FilterChip(selected = !isLocal, onClick = { isLocal = false }, label = { Text("UTC") })
        }

        Spacer(Modifier.height(10.dp))
        Text("Predicted $eventLabel Times", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))

        if (windowStartD == null || windowEndD == null) {
            Text(
                "No astronomical night at this location/date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (events.isEmpty()) {
            Text(
                "No predicted events tonight",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.forEach { eventD ->
                val displayTz = if (isLocal) timeZone else TimeZone.UTC
                Text(
                    "$eventLabel: ${AstronomyService.dToVsxDateTimeString(eventD, displayTz)}${if (!isLocal) " UTC" else ""}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (location != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))
            Text(
                "Visibility Tonight — ${AstronomyService.dToLocalDateString(observingD, timeZone)}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            NightArcChart(
                skyObj       = skyObj,
                location     = location,
                observingD   = observingD,
                modifier     = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                timeZone     = timeZone,
                windowStartD = windowStartD,
                windowEndD   = windowEndD,
                markerTimesD = events
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
