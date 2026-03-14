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
    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                isRunning = true
                isError = false
                scope.launch {
                    repository.updateCatalog { status ->
                        statusText = status
                        isError = status.startsWith("DSO data loading failed")
                    }
                    isRunning = false
                }
            },
            enabled = !isRunning
        ) {
            Text("Update DSO")
        }

        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
