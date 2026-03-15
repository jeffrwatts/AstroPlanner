package com.islandskiesastro.astroplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.roundToInt

private fun computeFov(
    telescope: Telescope,
    optElem: OpticalElement,
    camera: Camera
): Pair<Double, Double> {
    val fl   = telescope.focalLength * optElem.factor
    val fovW = 2.0 * atan(camera.sensorWidth  / (2.0 * fl)) * (180.0 / PI)
    val fovH = 2.0 * atan(camera.sensorHeight / (2.0 * fl)) * (180.0 / PI)
    return Pair(fovW, fovH)
}

private fun skyViewUrl(ra: Double, dec: Double, sizeDeg: Double, scaling: String) =
    "https://skyview.gsfc.nasa.gov/current/cgi/runquery.pl" +
    "?Position=$ra,$dec&Size=$sizeDeg&Pixels=1000&Rotation=0" +
    "&Scaling=$scaling&Return=PNG&coordinates=J2000&Survey=DSS"

private fun Double.fmt2(): String {
    val rounded = (this * 100).roundToInt().toDouble() / 100.0
    val str = rounded.toString()
    val dot = str.indexOf('.')
    return if (dot < 0) "$str.00"
    else str.padEnd(dot + 3, '0').substring(0, dot + 3)
}

private val scalingOptions = listOf("Linear", "Log", "Sqrt", "HistEq", "LogLog")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldOfViewScreen(
    skyObj: SkyObject,
    onBack: () -> Unit
) {
    var selectedTelescope by remember { mutableStateOf(EquipmentRepository.telescopes.first()) }
    var selectedOptElem   by remember { mutableStateOf(EquipmentRepository.opticalElements.first()) }
    var selectedCamera    by remember { mutableStateOf(EquipmentRepository.cameras.first()) }
    var scaling           by remember { mutableStateOf("Linear") }
    var imageBytes        by remember { mutableStateOf<ByteArray?>(null) }
    // FOV dims that correspond to the currently displayed image
    var displayedFovW     by remember { mutableStateOf(0.0) }
    var displayedFovH     by remember { mutableStateOf(0.0) }
    var displayedImageSize by remember { mutableStateOf(1.0) }
    var isLoading         by remember { mutableStateOf(false) }
    var errorMsg          by remember { mutableStateOf<String?>(null) }

    val httpClient = remember { HttpClient() }
    DisposableEffect(Unit) { onDispose { httpClient.close() } }

    val scope = rememberCoroutineScope()

    // FOV for current control selections (updates live for the info label)
    val (fovW, fovH) = computeFov(selectedTelescope, selectedOptElem, selectedCamera)

    fun fetchImage() {
        val tel    = selectedTelescope
        val opt    = selectedOptElem
        val cam    = selectedCamera
        val scl    = scaling
        val (fw, fh) = computeFov(tel, opt, cam)
        val imgSize  = max(fw, fh) * 1.5
        scope.launch {
            isLoading = true
            errorMsg  = null
            imageBytes = null
            imageBytes = try {
                val url = skyViewUrl(skyObj.obj.ra, skyObj.obj.dec, imgSize, scl)
                httpClient.get(url).readRawBytes()
            } catch (e: Exception) {
                errorMsg = "Failed to load image: ${e.message}"
                null
            }
            if (imageBytes != null) {
                displayedFovW      = fw
                displayedFovH      = fh
                displayedImageSize = imgSize
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (imageBytes != null) {
                AsyncImage(
                    model = imageBytes,
                    contentDescription = "Sky survey image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rectW = (displayedFovW / displayedImageSize * size.width).toFloat()
                        .coerceIn(0f, size.width)
                    val rectH = (displayedFovH / displayedImageSize * size.height).toFloat()
                        .coerceIn(0f, size.height)
                    drawRect(
                        color   = Color.Red,
                        topLeft = Offset((size.width - rectW) / 2f, (size.height - rectH) / 2f),
                        size    = Size(rectW, rectH),
                        style   = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            if (isLoading) CircularProgressIndicator()
            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Controls
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Spacer(Modifier.height(4.dp))
            EquipmentRow(
                label = "Telescope",
                options = EquipmentRepository.telescopes,
                selected = selectedTelescope,
                displayName = { it.displayName },
                onSelect = { selectedTelescope = it }
            )
            Spacer(Modifier.height(8.dp))
            EquipmentRow(
                label = "Optical Element",
                options = EquipmentRepository.opticalElements,
                selected = selectedOptElem,
                displayName = { it.displayName },
                onSelect = { selectedOptElem = it }
            )
            Spacer(Modifier.height(8.dp))
            EquipmentRow(
                label = "Camera",
                options = EquipmentRepository.cameras,
                selected = selectedCamera,
                displayName = { it.displayName },
                onSelect = { selectedCamera = it }
            )
            Spacer(Modifier.height(8.dp))
            EquipmentRow(
                label = "Scaling",
                options = scalingOptions,
                selected = scaling,
                displayName = { it },
                onSelect = { scaling = it }
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "FOV: ${fovW.fmt2()}° \u00D7 ${fovH.fmt2()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { fetchImage() }, enabled = !isLoading) {
                    Text("Load Image")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EquipmentRow(
    label: String,
    options: List<T>,
    selected: T,
    displayName: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(120.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = displayName(selected),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(displayName(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
