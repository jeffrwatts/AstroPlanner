package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    locationService: LocationService,
    orientationService: OrientationService,
    hasLocationPermission: Boolean,
    repository: CelestialObjectRepository
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val location by locationService.location.collectAsState()
    var useGpsLocation     by rememberSaveable { mutableStateOf(true) }
    var savedLocationIndex by rememberSaveable { mutableIntStateOf(0) }

    val effectiveLocation: LocationData? = if (useGpsLocation) location
        else SAVED_LOCATIONS[savedLocationIndex].toLocationData()

    val effectiveTimeZone: TimeZone = if (useGpsLocation) TimeZone.currentSystemDefault()
        else TimeZone.of(SAVED_LOCATIONS[savedLocationIndex].timeZoneId)

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Back action and title override supplied by the active screen when a detail is open.
    var backAction  by remember { mutableStateOf<(() -> Unit)?>(null) }
    var detailTitle by remember { mutableStateOf<String?>(null) }
    // Clear both whenever the user switches tabs.
    LaunchedEffect(selectedIndex) { backAction = null; detailTitle = null }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) locationService.startUpdates()
        onDispose { locationService.stopUpdates() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Screen.all.forEachIndexed { index, screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(detailTitle ?: Screen.all[selectedIndex].title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        backAction?.let { action ->
                            IconButton(onClick = action) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (Screen.all[selectedIndex]) {
                    Screen.SkyPlanner -> SkyPlannerScreen(
                        effectiveLocation, hasLocationPermission, repository,
                        onBackActionChanged = { backAction = it },
                        onTitleChanged = { detailTitle = it },
                        timeZone = effectiveTimeZone
                    )
                    Screen.Jupiter    -> JupiterScreen(effectiveLocation, hasLocationPermission, timeZone = effectiveTimeZone)
                    Screen.Info       -> InfoScreen(effectiveLocation, hasLocationPermission, timeZone = effectiveTimeZone)
                    Screen.AltAzm     -> AltAzmScreen(orientationService, effectiveLocation, hasLocationPermission)
                    Screen.Update     -> UpdateScreen(repository)
                    Screen.Settings   -> SettingsScreen(
                        useGpsLocation     = useGpsLocation,
                        savedLocationIndex = savedLocationIndex,
                        onUseGpsChanged    = { useGpsLocation = it },
                        onSavedIndexChanged = { savedLocationIndex = it }
                    )
                }
            }
        }
    }
}
