package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UpdateScreen(
    repository: CelestialObjectRepository,
    equipmentRepository: EquipmentRepository,
    onBackActionChanged: ((() -> Unit)?) -> Unit = {}
) {
    var showAddObject by remember { mutableStateOf(false) }

    var catalogRunning by remember { mutableStateOf(false) }
    var catalogStatus by remember { mutableStateOf("") }
    var catalogError by remember { mutableStateOf(false) }

    var imgRunning by remember { mutableStateOf(false) }
    var imgStatus by remember { mutableStateOf("") }
    var imgError by remember { mutableStateOf(false) }

    var compStarsRunning by remember { mutableStateOf(false) }
    var compStarsStatus by remember { mutableStateOf("") }
    var compStarsError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(showAddObject) {
        if (!showAddObject) onBackActionChanged(null)
        // When showAddObject=true, AddObjectScreen manages the back action itself
    }

    if (showAddObject) {
        AddObjectScreen(
            repository          = repository,
            equipmentRepository = equipmentRepository,
            onBackActionChanged = onBackActionChanged,
            onDismiss           = { showAddObject = false },
            onSaved             = { showAddObject = false }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { showAddObject = true }) {
            Text("Add Custom Object")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                catalogRunning = true
                catalogError = false
                scope.launch {
                    repository.updateCatalog { status ->
                        catalogStatus = status
                        catalogError = status.startsWith("Catalog update failed")
                    }
                    catalogRunning = false
                }
            },
            enabled = !catalogRunning
        ) {
            Text("Update Catalog")
        }

        if (catalogStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Catalog: $catalogStatus",
                style = MaterialTheme.typography.bodyMedium,
                color = if (catalogError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                compStarsRunning = true
                compStarsError = false
                scope.launch {
                    repository.updateAllComparisonStars { status ->
                        compStarsStatus = status
                        compStarsError = status.startsWith("Comparison stars update failed")
                    }
                    compStarsRunning = false
                }
            },
            enabled = !compStarsRunning
        ) {
            Text("Fetch All Comparison Stars")
        }

        if (compStarsStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Comparison Stars: $compStarsStatus",
                style = MaterialTheme.typography.bodyMedium,
                color = if (compStarsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                imgRunning = true
                imgError = false
                scope.launch {
                    repository.updateImages { status ->
                        imgStatus = status
                        imgError = status.startsWith("Image update failed")
                    }
                    imgRunning = false
                }
            },
            enabled = !imgRunning
        ) {
            Text("Update Images")
        }

        if (imgStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Images: $imgStatus",
                style = MaterialTheme.typography.bodyMedium,
                color = if (imgError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
