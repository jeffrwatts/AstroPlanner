package com.islandskiesastro.landmarkid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CameraHeadingScreen(
    heading: Double,
    altitude: Double,
    accuracy: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        CameraPreview(modifier = Modifier.fillMaxSize())

        val accuracyText = when (accuracy) {
            3 -> "High"
            2 -> "Medium"
            1 -> "Low"
            else -> "Unreliable"
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Row {
                ReadoutItem("Hdg", "${heading.fmt1()}°")
                ReadoutItem("Alt", "${altitude.fmt1()}°")
                ReadoutItem("Accuracy", accuracyText)
            }
        }

        CompassAltitudeRuler(
            heading = heading,
            altitude = altitude,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ReadoutItem(label: String, value: String) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 18.sp)
    }
}

// KMP-style 1-decimal-place formatter kept for consistency with AstroPlanner's convention
private fun Double.fmt1(): String {
    val rounded = (this * 10.0).roundToInt()
    val sign = if (rounded < 0) "-" else ""
    val absVal = abs(rounded)
    return "$sign${absVal / 10}.${absVal % 10}"
}
