package com.islandskiesastro.astroplanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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
        MainScreen(locationService, orientationService, hasLocationPermission, repository, equipmentRepository, planRepository)
    }
}
