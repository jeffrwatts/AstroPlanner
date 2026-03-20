package com.islandskiesastro.astroplanner

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val locationService = remember { LocationService() }
    val orientationService = remember { OrientationService() }
    val driverFactory = remember { DatabaseDriverFactory() }
    val imageStorage = remember { ImageStorage() }
    val repository = remember { CelestialObjectRepository(driverFactory, imageStorage) }
    val equipmentRepository = remember { EquipmentRepository(driverFactory) }
    val planRepository = remember { PlanRepository(driverFactory) }
    App(locationService, orientationService, hasLocationPermission = true, repository = repository, equipmentRepository = equipmentRepository, planRepository = planRepository)
}
