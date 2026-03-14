package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(locationService: LocationService, hasLocationPermission: Boolean) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val location by locationService.location.collectAsState()

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) locationService.startUpdates()
        onDispose { locationService.stopUpdates() }
    }

    Scaffold { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavigationRail {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Screen.all.forEachIndexed { index, screen ->
                        NavigationRailItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (Screen.all[selectedIndex]) {
                    Screen.SkyPlanner -> SkyPlannerScreen(location, hasLocationPermission)
                    Screen.Info       -> InfoScreen(location, hasLocationPermission)
                    Screen.AltAzm     -> AltAzmScreen(location, hasLocationPermission)
                    Screen.Update     -> UpdateScreen(location, hasLocationPermission)
                    Screen.Settings   -> SettingsScreen(location, hasLocationPermission)
                }
            }
        }
    }
}
