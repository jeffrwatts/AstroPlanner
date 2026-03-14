package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import kotlin.math.abs

@Composable
internal fun DetailScreen(
    skyObj: SkyObject,
    image: CelestialObjectImage?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        if (image?.fullPath != null) {
            AsyncImage(
                model = image.fullPath.toPath(),
                contentDescription = skyObj.obj.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Fit
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(skyObj.obj.displayName, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "RA: ${skyObj.obj.ra.formatRa()}   Dec: ${skyObj.obj.dec.formatDec()}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            val typeStr = skyObj.obj.type.name.lowercase()
                .replaceFirstChar { it.uppercase() } +
                (skyObj.obj.subType?.let { " / $it" } ?: "")
            Text(typeStr, style = MaterialTheme.typography.bodyMedium)
            if (!skyObj.obj.constellation.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Constellation: ${skyObj.obj.constellation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Double.formatRa(): String {
    val hours = this / 15.0
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    val s = (hours - h - m / 60.0) * 3600
    return "${h}h ${m}m ${s.toInt()}s"
}

private fun Double.formatDec(): String {
    val sign = if (this < 0) "-" else "+"
    val a = abs(this)
    val d = a.toInt()
    val m = ((a - d) * 60).toInt()
    val s = (a - d - m / 60.0) * 3600
    return "$sign${d}° ${m}' ${s.toInt()}\""
}
