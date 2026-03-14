package com.islandskiesastro.astroplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UpdateScreen(repository: CelestialObjectRepository) {
    var dsoRunning by remember { mutableStateOf(false) }
    var dsoStatus by remember { mutableStateOf("") }
    var dsoError by remember { mutableStateOf(false) }

    var imgRunning by remember { mutableStateOf(false) }
    var imgStatus by remember { mutableStateOf("") }
    var imgError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                dsoRunning = true
                dsoError = false
                scope.launch {
                    repository.updateCatalog { status ->
                        dsoStatus = status
                        dsoError = status.startsWith("DSO data loading failed")
                    }
                    dsoRunning = false
                }
            },
            enabled = !dsoRunning
        ) {
            Text("Update DSO")
        }

        if (dsoStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "DSO: $dsoStatus",
                style = MaterialTheme.typography.bodyMedium,
                color = if (dsoError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                imgRunning = true
                imgError = false
                scope.launch {
                    repository.updateImages { status ->
                        imgStatus = status
                        imgError = status.startsWith("Image update failed")
                    }
                    imgRunning = false
                }
            },
            enabled = !imgRunning
        ) {
            Text("Update Images")
        }

        if (imgStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Images: $imgStatus",
                style = MaterialTheme.typography.bodyMedium,
                color = if (imgError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
