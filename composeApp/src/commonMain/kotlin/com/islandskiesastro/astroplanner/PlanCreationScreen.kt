package com.islandskiesastro.astroplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan

@Composable
internal fun PlanCreationScreen(
    preFill: CelestialObject?,
    location: LocationData?,
    locationName: String,
    equipmentRepository: EquipmentRepository,
    repository: CelestialObjectRepository,
    planRepository: PlanRepository,
    timeZone: TimeZone,
    onBack: () -> Unit,
    onPlanGenerated: (ObservingPlan) -> Unit
) {
    val allObjects = remember { repository.getAllObjects() }
    val allConfigs = remember { equipmentRepository.getAll() }
    val apiKey     = remember { planRepository.getApiKey() }

    var searchQuery    by remember { mutableStateOf(preFill?.displayName ?: "") }
    var selectedTarget by remember { mutableStateOf(preFill) }
    var showDropdown   by remember { mutableStateOf(false) }

    var bortleScale by remember { mutableIntStateOf(5) }
    val filters     = remember { planRepository.getFilters() }

    var showPrompt   by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentPrompt = remember(selectedTarget, location, locationName, bortleScale, filters, timeZone) {
        val t = selectedTarget ?: return@remember null
        buildPrompt(target = t, configs = allConfigs, location = location, locationName = locationName,
            bortleScale = bortleScale, filters = filters, timeZone = timeZone)
    }

    val filteredObjects = remember(searchQuery) {
        if (searchQuery.isBlank() || selectedTarget != null) emptyList()
        else allObjects.filter { obj ->
            obj.displayName.contains(searchQuery, ignoreCase = true) ||
                    obj.objectId.contains(searchQuery, ignoreCase = true)
        }.take(6)
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Target ────────────────────────────────────────────────────────────
        Text("Target", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { q ->
                searchQuery    = q
                selectedTarget = null
                showDropdown   = q.isNotBlank()
            },
            label     = { Text("Search objects") },
            singleLine = true,
            modifier  = Modifier.fillMaxWidth()
        )
        if (showDropdown && filteredObjects.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .verticalScroll(rememberScrollState())
            ) {
                filteredObjects.forEachIndexed { idx, obj ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTarget = obj
                                searchQuery    = obj.displayName
                                showDropdown   = false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(obj.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${obj.objectId}  •  ${obj.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (idx < filteredObjects.lastIndex) HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else if (showDropdown && searchQuery.isNotBlank() && selectedTarget == null) {
            Text(
                "No matches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ── Site ──────────────────────────────────────────────────────────────
        Text("Observing Site", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val siteText = if (location != null)
            "$locationName  •  Alt: ${location.altitude.toInt()}m ASL"
        else
            "Location unavailable"
        Text(siteText, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ── Bortle Scale ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bortle Scale", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bortleScale.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = bortleScale.toFloat(),
            onValueChange = { bortleScale = it.toInt() },
            valueRange = 1f..9f,
            steps = 7,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Prompt preview ────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(checked = showPrompt, onCheckedChange = { showPrompt = it })
            Text("Preview prompt", style = MaterialTheme.typography.bodyMedium)
        }
        if (showPrompt && currentPrompt != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(currentPrompt, style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }

        // ── Generate ──────────────────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))

        val canGenerate = selectedTarget != null &&
                allConfigs.isNotEmpty() &&
                apiKey.isNotBlank() &&
                !isGenerating

        val generateTooltip = when {
            apiKey.isBlank()         -> "Add your Anthropic API key in Settings"
            allConfigs.isEmpty()     -> "Configure equipment in Settings first"
            selectedTarget == null   -> "Select a target first"
            else                     -> null
        }

        if (generateTooltip != null && !isGenerating) {
            Text(
                generateTooltip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = {
                val prompt = currentPrompt ?: return@Button
                val target = selectedTarget ?: return@Button
                scope.launch {
                    isGenerating = true
                    errorMessage = null
                    val result = AnthropicService.generatePlan(apiKey, prompt, AnthropicService.Model.OPUS)
                    result.onSuccess { markdown ->
                        val plan = ObservingPlan(
                            createdAtMs       = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                            targetObjectId    = target.objectId,
                            targetDisplayName = target.displayName,
                            equipmentConfigId = 0L,
                            equipmentSummary  = "",
                            locationName      = locationName,
                            locationAltitudeM = location?.altitude ?: 0.0,
                            bortleScale       = bortleScale,
                            observingGoal     = "Imaging",
                            availableMinutes  = 0,
                            planMarkdown      = markdown
                        )
                        onPlanGenerated(plan)
                    }
                    result.onFailure { errorMessage = it.message ?: "Unknown error" }
                    isGenerating = false
                }
            },
            enabled  = canGenerate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Plan")
        }

        if (isGenerating) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text("Generating your plan…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (errorMessage != null) {
            Text(
                errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = {
                    val prompt = currentPrompt ?: return@OutlinedButton
                    val target = selectedTarget ?: return@OutlinedButton
                    scope.launch {
                        isGenerating = true
                        errorMessage = null
                        val result = AnthropicService.generatePlan(apiKey, prompt, AnthropicService.Model.OPUS)
                        result.onSuccess { markdown ->
                            val plan = ObservingPlan(
                                createdAtMs       = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                targetObjectId    = target.objectId,
                                targetDisplayName = target.displayName,
                                equipmentConfigId = 0L,
                                equipmentSummary  = "",
                                locationName      = locationName,
                                locationAltitudeM = location?.altitude ?: 0.0,
                                bortleScale       = bortleScale,
                                observingGoal     = "Imaging",
                                availableMinutes  = 0,
                                planMarkdown      = markdown
                            )
                            onPlanGenerated(plan)
                        }
                        result.onFailure { errorMessage = it.message ?: "Unknown error" }
                        isGenerating = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try Again")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun buildPrompt(
    target: CelestialObject,
    configs: List<EquipmentConfig>,
    location: LocationData?,
    locationName: String,
    bortleScale: Int,
    filters: String,
    timeZone: TimeZone
): String {
    val typeStr = target.type.name.lowercase().replaceFirstChar { it.uppercase() } +
            (target.subType?.let { " / $it" } ?: "")
    val constellationStr = target.constellation?.let { "Constellation: $it\n" } ?: ""
    val magnitudeStr = target.magnitude?.let { "• Magnitude: ${it.fmt1()}\n" } ?: ""
    val angularSizeStr = when {
        target.angularSizeMajor != null && target.angularSizeMinor != null ->
            "• Angular size: ${target.angularSizeMajor.fmt1()}' × ${target.angularSizeMinor.fmt1()}'\n"
        target.angularSizeMajor != null ->
            "• Angular size: ${target.angularSizeMajor.fmt1()}'\n"
        else -> ""
    }

    val locationStr = if (locationName.isNotBlank()) "• Location: $locationName\n" else ""
    val altStr = if (location != null)
        "• Altitude: ${location.altitude.toInt()}m ASL\n"
    else
        "• Altitude: Unknown\n"

    val equipmentSection = configs.joinToString("\n\n") { config ->
        val effectiveFL = config.focalLength * config.focalReducerFactor
        val fRatio = if (config.aperture > 0) effectiveFL / config.aperture else 0.0
        val sensorWmm = config.pixelSize * config.resolutionWidth / 1000.0
        val sensorHmm = config.pixelSize * config.resolutionHeight / 1000.0
        val fovWdeg = if (effectiveFL > 0) 2.0 * atan(sensorWmm / (2.0 * effectiveFL)) * (180.0 / PI) else 0.0
        val fovHdeg = if (effectiveFL > 0) 2.0 * atan(sensorHmm / (2.0 * effectiveFL)) * (180.0 / PI) else 0.0
        val fovWmin = fovWdeg * 60.0
        val fovHmin = fovHdeg * 60.0
        val plateScale = if (effectiveFL > 0) (config.pixelSize / effectiveFL) * 206.265 else 0.0
        val colorType = if (isCameraColor(config.cameraName)) "Color (OSC)" else "Mono"
        buildString {
            appendLine("### ${config.name}")
            appendLine("• OTA: ${config.otaName.ifBlank { "Unknown" }}, Focal Length: ${config.focalLength.toInt()}mm, f/${fRatio.fmt1()}")
            appendLine("• Camera: ${config.cameraName.ifBlank { "Unknown" }} ($colorType), ${config.resolutionWidth}×${config.resolutionHeight}px @ ${config.pixelSize}µm")
            appendLine("• Effective focal length: ${effectiveFL.toInt()}mm (reducer: ${config.focalReducerFactor}x)")
            append("• Field of view: ${fovWmin.fmt1()}' × ${fovHmin.fmt1()}'   Plate scale: ${plateScale.fmt2()}\"/px")
        }
    }

    return """
## Target
${target.displayName} — $typeStr
${constellationStr}RA: ${target.ra.formatRa()}   Dec: ${target.dec.formatDec()}
${magnitudeStr}${angularSizeStr}
## Available Equipment Configurations
$equipmentSection

## Observing Site
${locationStr}${altStr}• Bortle scale: $bortleScale/9

## Session Parameters
• Goal: Imaging
• Available filters: ${filters.lines().filter { it.isNotBlank() }.joinToString(", ").ifBlank { "not specified" }}
    """.trimIndent()
}

private fun isCameraColor(cameraName: String): Boolean {
    val lower = cameraName.lowercase()
    return lower.contains(" mc") || lower.contains("-mc") || lower.contains("mc ") ||
           lower.contains("color") || lower.contains("colour") || lower.contains("osc")
}

private fun Double.formatRa(): String {
    val hours = this / 15.0
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    val s = ((hours - h - m / 60.0) * 3600).toInt()
    return "${h}h ${m}m ${s}s"
}

private fun Double.formatDec(): String {
    val sign = if (this < 0) "-" else "+"
    val a = abs(this)
    val d = a.toInt()
    val m = ((a - d) * 60).toInt()
    val s = ((a - d - m / 60.0) * 3600).toInt()
    return "$sign${d}° ${m}' ${s}\""
}

// Format to 1 decimal place without String.format (KMP-safe)
private fun Double.fmt1(): String {
    val rounded = ((this * 10).toLong().toDouble()) / 10.0
    val str = rounded.toString()
    val dot = str.indexOf('.')
    return if (dot < 0) "$str.0"
    else str.padEnd(dot + 2, '0').substring(0, dot + 2)
}

// Format to 2 decimal places without String.format (KMP-safe)
private fun Double.fmt2(): String {
    val rounded = ((this * 100).toLong().toDouble()) / 100.0
    val str = rounded.toString()
    val dot = str.indexOf('.')
    return if (dot < 0) "$str.00"
    else str.padEnd(dot + 3, '0').substring(0, dot + 3)
}
