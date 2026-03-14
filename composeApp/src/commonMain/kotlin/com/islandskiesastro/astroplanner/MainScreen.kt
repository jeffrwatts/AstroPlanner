package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(locationService: LocationService, hasLocationPermission: Boolean) {
    var selectedScreen by rememberSaveable { mutableStateOf<Screen>(Screen.SkyPlanner) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.all.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedScreen) {
                Screen.SkyPlanner -> SkyPlannerScreen()
                Screen.Info       -> InfoScreen(locationService, hasLocationPermission)
                Screen.AltAzm     -> AltAzmScreen()
                Screen.Update     -> UpdateScreen()
                Screen.Settings   -> SettingsScreen()
            }
        }
    }
}
