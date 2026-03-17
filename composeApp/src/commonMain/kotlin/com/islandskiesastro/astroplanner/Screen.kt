package com.islandskiesastro.astroplanner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val title: String,
    val icon: ImageVector
) {
    object SkyPlanner : Screen("Astro Planner", Icons.Default.Star)
    object Jupiter    : Screen("Jupiter",        Icons.Default.Public)
    object Info       : Screen("Info",           Icons.Default.MyLocation)
    object AltAzm     : Screen("Alt/Azm Tool",   Icons.Default.Explore)
    object Update     : Screen("Update",          Icons.Default.Refresh)
    object Settings   : Screen("Settings",        Icons.Default.Settings)

    companion object {
        val all: List<Screen> = listOf(SkyPlanner, Jupiter, Info, AltAzm, Update, Settings)
    }
}
