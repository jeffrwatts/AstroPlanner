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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val selectableTypes = listOf(
    ObjectType.GALAXY,
    ObjectType.NEBULA,
    ObjectType.CLUSTER,
    ObjectType.STAR,
    ObjectType.UNKNOWN
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddObjectScreen(
    repository: CelestialObjectRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var objectIdInput by remember { mutableStateOf("") }
    var lookupState by remember { mutableStateOf<LookupState>(LookupState.Idle) }

    var displayName by remember { mutableStateOf("") }
    var raInput by remember { mutableStateOf(TextFieldValue("")) }
    var decInput by remember { mutableStateOf(TextFieldValue("")) }
    var decPositive by remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf(ObjectType.UNKNOWN) }
    var magnitudeInput by remember { mutableStateOf("") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val fieldsEnabled = lookupState !is LookupState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Add Custom Object", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = objectIdInput,
            onValueChange = {
                objectIdInput = it
                lookupState = LookupState.Idle
                saveError = ""
            },
            label = { Text("Object ID") },
            placeholder = { Text("e.g. NGC 6888, M31, IC 434") },
            isError = lookupState is LookupState.AlreadyExists || lookupState is LookupState.NotFound,
            supportingText = when (lookupState) {
                is LookupState.AlreadyExists -> ({ Text("Already in catalog", color = MaterialTheme.colorScheme.error) })
                is LookupState.NotFound      -> ({ Text("Not found in SIMBAD", color = MaterialTheme.colorScheme.error) })
                is LookupState.LookupError   -> ({ Text((lookupState as LookupState.LookupError).message, color = MaterialTheme.colorScheme.error) })
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = fieldsEnabled,
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    lookupState = LookupState.Loading
                    saveError = ""
                    when (val result = repository.lookupSimbad(objectIdInput)) {
                        is SimbadResult.Success -> {
                            displayName  = result.displayName
                            raInput      = TextFieldValue(result.ra.toRaString())
                            decPositive  = result.dec >= 0
                            decInput     = TextFieldValue(result.dec.toDecBodyString())
                            selectedType = result.type
                            lookupState  = LookupState.Found
                        }
                        is SimbadResult.AlreadyExists -> lookupState = LookupState.AlreadyExists
                        is SimbadResult.NotFound      -> lookupState = LookupState.NotFound
                        is SimbadResult.Error         -> lookupState = LookupState.LookupError(result.message)
                    }
                }
            },
            enabled = objectIdInput.isNotBlank() && lookupState !is LookupState.Loading,
            modifier = Modifier.align(Alignment.End)
        ) {
            if (lookupState is LookupState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            }
            Text("Look Up")
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; saveError = "" },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = fieldsEnabled,
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // RA field
        OutlinedTextField(
            value = raInput,
            onValueChange = { raInput = autoInsertColon(raInput, it); saveError = "" },
            label = { Text("RA (HH:MM:SS)") },
            placeholder = { Text("e.g. 20:12:07") },
            modifier = Modifier.fillMaxWidth(),
            enabled = fieldsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // Dec field with +/− toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { decPositive = !decPositive },
                enabled = fieldsEnabled,
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
                enabled = fieldsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }

        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = typeDropdownExpanded,
            onExpandedChange = { if (fieldsEnabled) typeDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                enabled = fieldsEnabled
            )
            ExposedDropdownMenu(
                expanded = typeDropdownExpanded,
                onDismissRequest = { typeDropdownExpanded = false }
            ) {
                selectableTypes.forEach { type ->
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
            enabled = fieldsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

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
            Button(
                onClick = {
                    val ra  = parseRa(raInput.text)
                    val decAbs = parseDecBody(decInput.text)
                    val dec = decAbs?.let { if (decPositive) it else -it }
                    val mag = if (magnitudeInput.isBlank()) null else magnitudeInput.toDoubleOrNull()
                    when {
                        objectIdInput.isBlank()                          -> saveError = "Object ID is required"
                        displayName.isBlank()                            -> saveError = "Display name is required"
                        ra == null                                       -> saveError = "RA must be HH:MM:SS (e.g. 20:12:07)"
                        dec == null                                      -> saveError = "Dec must be DD:MM:SS (e.g. 38:21:09)"
                        magnitudeInput.isNotBlank() && mag == null       -> saveError = "Magnitude must be a valid number"
                        else -> {
                            repository.addUserObject(
                                displayName = displayName.trim(),
                                objectId    = objectIdInput.trim().lowercase().replace(" ", ""),
                                ra          = ra,
                                dec         = dec,
                                type        = selectedType,
                                magnitude   = mag
                            )
                            onSaved()
                        }
                    }
                },
                enabled = fieldsEnabled
            ) { Text("Save") }
        }
    }
}

private sealed class LookupState {
    object Idle : LookupState()
    object Loading : LookupState()
    object Found : LookupState()
    object AlreadyExists : LookupState()
    object NotFound : LookupState()
    data class LookupError(val message: String) : LookupState()
}
