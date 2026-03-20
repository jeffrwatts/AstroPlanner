package com.islandskiesastro.astroplanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    useGpsLocation: Boolean,
    savedLocationIndex: Int,
    onUseGpsChanged: (Boolean) -> Unit,
    onSavedIndexChanged: (Int) -> Unit,
    equipmentRepository: EquipmentRepository,
    planRepository: PlanRepository
) {
    var configs by remember { mutableStateOf(equipmentRepository.getAll()) }
    fun reload() { configs = equipmentRepository.getAll() }

    var apiKeyDraft by remember { mutableStateOf(planRepository.getApiKey()) }
    var showApiKey  by remember { mutableStateOf(false) }

    val filterList  = remember {
        mutableStateListOf<String>().also { list ->
            list.addAll(planRepository.getFilters().lines().filter { it.isNotBlank() })
        }
    }
    var newFilterDraft by remember { mutableStateOf("") }

    fun saveFilters() { planRepository.setFilters(filterList.joinToString("\n")) }

    // Dialog state: null = closed, null initial = Add, non-null initial = Edit
    var dialogInitial by remember { mutableStateOf<EquipmentConfig?>(null) }
    var showDialog    by remember { mutableStateOf(false) }

    if (showDialog) {
        EquipmentConfigDialog(
            initial   = dialogInitial,
            onDismiss = { showDialog = false },
            onSave    = { config ->
                if (config.id == 0L) equipmentRepository.insert(config)
                else                 equipmentRepository.update(config)
                reload()
                showDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Equipment Configurations ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Equipment Configurations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = {
                dialogInitial = null
                showDialog    = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add equipment")
            }
        }

        if (configs.isEmpty()) {
            Text(
                "No configurations yet — tap + to add one",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SettingsList {
                configs.forEachIndexed { index, config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(config.name, style = MaterialTheme.typography.bodyLarge)
                            val subtitle = buildList {
                                if (config.otaName.isNotBlank())    add(config.otaName)
                                if (config.cameraName.isNotBlank()) add(config.cameraName)
                                add("${config.focalLength}mm · ${config.focalReducerFactor}x · ${config.pixelSize}µm")
                            }.joinToString(" · ")
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            dialogInitial = config
                            showDialog    = true
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = {
                            equipmentRepository.delete(config.id)
                            reload()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (index < configs.lastIndex) SettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Filter Inventory ──────────────────────────────────────────────────
        Text(
            "Filter Inventory",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        if (filterList.isEmpty()) {
            Text(
                "No filters added yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SettingsList {
                filterList.forEachIndexed { index, filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            filter,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = {
                            filterList.removeAt(index)
                            saveFilters()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove filter",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (index < filterList.lastIndex) SettingsDivider()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newFilterDraft,
                onValueChange = { newFilterDraft = it },
                label = { Text("Add filter") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val trimmed = newFilterDraft.trim()
                    if (trimmed.isNotBlank() && !filterList.contains(trimmed)) {
                        filterList.add(trimmed)
                        saveFilters()
                    }
                    newFilterDraft = ""
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add filter")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Location Source ───────────────────────────────────────────────────
        Text(
            "Location Source",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Use Device GPS", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = useGpsLocation, onCheckedChange = onUseGpsChanged)
        }

        AnimatedVisibility(visible = !useGpsLocation) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Saved Locations",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                SettingsList {
                    SAVED_LOCATIONS.forEachIndexed { index, loc ->
                        val selected = savedLocationIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSavedIndexChanged(index) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(loc.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${loc.latitude}°, ${loc.longitude}°  •  ${loc.timeZoneId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (index < SAVED_LOCATIONS.lastIndex) SettingsDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Anthropic API Key ─────────────────────────────────────────────────
        Text(
            "Anthropic API Key",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showApiKey) "Hide key" else "Show key"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { planRepository.setApiKey(apiKeyDraft.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API Key")
        }
    }
}

// ── Shared list container ─────────────────────────────────────────────────────

@Composable
private fun SettingsList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

// ── Add / Edit dialog ─────────────────────────────────────────────────────────

@Composable
private fun EquipmentConfigDialog(
    initial: EquipmentConfig?,
    onDismiss: () -> Unit,
    onSave: (EquipmentConfig) -> Unit
) {
    var name               by remember { mutableStateOf(initial?.name               ?: "") }
    var otaName            by remember { mutableStateOf(initial?.otaName            ?: "") }
    var cameraName         by remember { mutableStateOf(initial?.cameraName         ?: "") }
    var focalLength        by remember { mutableStateOf(initial?.focalLength?.toString()        ?: "") }
    var aperture           by remember { mutableStateOf(initial?.aperture?.toString()           ?: "") }
    var focalReducerFactor by remember { mutableStateOf(initial?.focalReducerFactor?.toString() ?: "1.0") }
    var pixelSize          by remember { mutableStateOf(initial?.pixelSize?.toString()          ?: "") }
    var resolutionWidth    by remember { mutableStateOf(initial?.resolutionWidth?.toString()    ?: "") }
    var resolutionHeight   by remember { mutableStateOf(initial?.resolutionHeight?.toString()   ?: "") }
    var error              by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Equipment" else "Edit Equipment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Config Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = otaName, onValueChange = { otaName = it },
                    label = { Text("OTA Name (e.g. Celestron C8)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cameraName, onValueChange = { cameraName = it },
                    label = { Text("Camera Name (e.g. ZWO ASI 294MC Pro)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = focalLength, onValueChange = { focalLength = it },
                    label = { Text("Focal Length (mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = aperture, onValueChange = { aperture = it },
                    label = { Text("Aperture (mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = focalReducerFactor, onValueChange = { focalReducerFactor = it },
                    label = { Text("Focal Reducer (1.0 = none)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pixelSize, onValueChange = { pixelSize = it },
                    label = { Text("Pixel Size (µm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = resolutionWidth, onValueChange = { resolutionWidth = it },
                    label = { Text("Resolution Width (px)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = resolutionHeight, onValueChange = { resolutionHeight = it },
                    label = { Text("Resolution Height (px)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val fl  = focalLength.toDoubleOrNull()
                val ap  = aperture.toDoubleOrNull()
                val frf = focalReducerFactor.toDoubleOrNull()
                val ps  = pixelSize.toDoubleOrNull()
                val rw  = resolutionWidth.toIntOrNull()
                val rh  = resolutionHeight.toIntOrNull()
                when {
                    name.isBlank() -> error = "Name is required"
                    fl  == null    -> error = "Invalid focal length"
                    ap  == null    -> error = "Invalid aperture"
                    frf == null    -> error = "Invalid focal reducer factor"
                    ps  == null    -> error = "Invalid pixel size"
                    rw  == null    -> error = "Invalid resolution width"
                    rh  == null    -> error = "Invalid resolution height"
                    else -> onSave(EquipmentConfig(
                        id                 = initial?.id ?: 0L,
                        name               = name.trim(),
                        otaName            = otaName.trim(),
                        cameraName         = cameraName.trim(),
                        focalLength        = fl,
                        aperture           = ap,
                        focalReducerFactor = frf,
                        pixelSize          = ps,
                        resolutionWidth    = rw,
                        resolutionHeight   = rh
                    ))
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
