package com.islandskiesastro.astroplanner

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS back gesture is handled natively by the system
}
