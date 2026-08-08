package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private val editableTypes = listOf(
    ObjectType.GALAXY,
    ObjectType.NEBULA,
    ObjectType.CLUSTER,
    ObjectType.STAR,
    ObjectType.VARIABLE_STAR,
    ObjectType.UNKNOWN
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditObjectScreen(
    obj: CelestialObject,
    repository: CelestialObjectRepository,
    equipmentRepository: EquipmentRepository,
    onBackActionChanged: ((() -> Unit)?) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var displayName by remember { mutableStateOf(obj.displayName) }
    var raInput     by remember { mutableStateOf(TextFieldValue(obj.ra.toRaString())) }
    var decInput    by remember { mutableStateOf(TextFieldValue(obj.dec.toDecBodyString())) }
    var decPositive by remember { mutableStateOf(obj.dec >= 0) }
    var selectedType by remember { mutableStateOf(obj.type) }
    var magnitudeInput by remember { mutableStateOf(obj.magnitude?.toString() ?: "") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }

    var variableTypeInput by remember { mutableStateOf(obj.subType ?: "") }
    var periodInput by remember { mutableStateOf(obj.variablePeriodDays?.toString() ?: "") }
    var epochInput by remember { mutableStateOf(obj.variableEpochJd?.toString() ?: "") }
    var epochIsMax by remember { mutableStateOf(obj.variableEpochType != "MIN") }

    var showFovPicker by remember { mutableStateOf(false) }

    LaunchedEffect(showFovPicker) {
        onBackActionChanged(
            if (showFovPicker) ({ showFovPicker = false })
            else ({ onDismiss() })
        )
    }

    if (showFovPicker) {
        val currentRa  = parseRa(raInput.text)
        val currentDec = parseDecBody(decInput.text)?.let { if (decPositive) it else -it }
        val fovSkyObj  = SkyObject(
            obj      = obj.copy(
                ra  = currentRa  ?: obj.ra,
                dec = currentDec ?: obj.dec
            ),
            altitude = 0.0,
            azimuth  = 0.0,
            transit  = "",
            isRising = false
        )
        FieldOfViewScreen(
            skyObj              = fovSkyObj,
            equipmentRepository = equipmentRepository,
            onBack              = { showFovPicker = false },
            onPickCoordinates   = { ra, dec ->
                raInput     = TextFieldValue(ra.toRaString())
                decPositive = dec >= 0
                decInput    = TextFieldValue(dec.toDecBodyString())
                showFovPicker = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Edit Object", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; saveError = "" },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        // RA indented to align its text input with the Dec text input below
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(64.dp))
            OutlinedTextField(
                value = raInput,
                onValueChange = { raInput = autoInsertColon(raInput, it); saveError = "" },
                label = { Text("RA (HH:MM:SS)") },
                placeholder = { Text("e.g. 20:12:07") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }

        Spacer(Modifier.height(8.dp))

        // Dec field with +/− toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { decPositive = !decPositive },
                modifier = Modifier.width(56.dp).height(56.dp)
            ) {
                Text(if (decPositive) "+" else "−")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = decInput,
                onValueChange = { decInput = autoInsertColon(decInput, it); saveError = "" },
                label = { Text("Dec (DD:MM:SS)") },
                placeholder = { Text("e.g. 38:21:09") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { showFovPicker = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Use FOV")
        }

        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = typeDropdownExpanded,
            onExpandedChange = { typeDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
            )
            ExposedDropdownMenu(
                expanded = typeDropdownExpanded,
                onDismissRequest = { typeDropdownExpanded = false }
            ) {
                editableTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        onClick = { selectedType = type; typeDropdownExpanded = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = magnitudeInput,
            onValueChange = { magnitudeInput = it; saveError = "" },
            label = { Text("Magnitude (optional)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        if (selectedType == ObjectType.VARIABLE_STAR) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = variableTypeInput,
                onValueChange = { variableTypeInput = it; saveError = "" },
                label = { Text("Variable Type") },
                placeholder = { Text("e.g. SXPHE, EA, Mira") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = periodInput,
                onValueChange = { periodInput = it; saveError = "" },
                label = { Text("Period (days)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = epochInput,
                onValueChange = { epochInput = it; saveError = "" },
                label = { Text("Epoch (Julian Date)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))
            Text("Epoch marks", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = epochIsMax,
                    onClick = { epochIsMax = true },
                    label = { Text("Maximum") }
                )
                FilterChip(
                    selected = !epochIsMax,
                    onClick = { epochIsMax = false },
                    label = { Text("Minimum") }
                )
            }
        }

        if (saveError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = {
                val ra     = parseRa(raInput.text)
                val decAbs = parseDecBody(decInput.text)
                val dec    = decAbs?.let { if (decPositive) it else -it }
                val mag    = if (magnitudeInput.isBlank()) null else magnitudeInput.toDoubleOrNull()
                val isVariable = selectedType == ObjectType.VARIABLE_STAR
                val period = periodInput.toDoubleOrNull()
                val epoch  = epochInput.toDoubleOrNull()
                when {
                    displayName.isBlank()                      -> saveError = "Display name is required"
                    ra == null                                 -> saveError = "RA must be HH:MM:SS (e.g. 20:12:07)"
                    dec == null                                -> saveError = "Dec must be DD:MM:SS (e.g. 38:21:09)"
                    magnitudeInput.isNotBlank() && mag == null -> saveError = "Magnitude must be a valid number"
                    isVariable && variableTypeInput.isBlank()  -> saveError = "Variable type is required"
                    isVariable && period == null                -> saveError = "Period must be a valid number of days"
                    isVariable && epoch == null                 -> saveError = "Epoch must be a valid Julian Date"
                    else -> {
                        repository.updateUserObject(
                            id          = obj.id,
                            displayName = displayName.trim(),
                            ra          = ra,
                            dec         = dec,
                            type        = selectedType,
                            magnitude   = mag,
                            subType     = if (isVariable) variableTypeInput.trim() else obj.subType,
                            constellation = obj.constellation,
                            variablePeriodDays = if (isVariable) period else null,
                            variableEpochJd    = if (isVariable) epoch else null,
                            variableEpochType  = if (isVariable) (if (epochIsMax) "MAX" else "MIN") else null
                        )
                        onSaved()
                    }
                }
            }) { Text("Save") }
        }
    }
}
