package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun App(
    locationService: LocationService,
    orientationService: OrientationService,
    hasLocationPermission: Boolean,
    repository: CelestialObjectRepository,
    equipmentRepository: EquipmentRepository,
    planRepository: PlanRepository
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var setupComplete by remember { mutableStateOf(!repository.isSystemCatalogEmpty()) }

        if (!setupComplete) {
            FirstRunSetup(repository = repository, onComplete = { setupComplete = true })
        } else {
            MainScreen(locationService, orientationService, hasLocationPermission, repository, equipmentRepository, planRepository)
        }
    }
}

@Composable
private fun FirstRunSetup(
    repository: CelestialObjectRepository,
    onComplete: () -> Unit
) {
    var statusMessage by remember { mutableStateOf("Initializing...") }

    LaunchedEffect(Unit) {
        repository.updateCatalog { msg -> statusMessage = msg }
        repository.updateVariableStars { msg -> statusMessage = msg }
        repository.updateAllComparisonStars { msg -> statusMessage = msg }
        // Image fetching is disabled on startup for now — run it from the Update page instead.
        onComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(32.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Setting Up AstroPlanner",
                    style = MaterialTheme.typography.titleLarge
                )
                CircularProgressIndicator()
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
