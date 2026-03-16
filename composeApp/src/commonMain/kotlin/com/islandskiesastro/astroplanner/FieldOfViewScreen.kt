package com.islandskiesastro.astroplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
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

private fun skyViewUrl(
    ra: Double, dec: Double, sizeDeg: Double, scaling: String, rotation: Float = 0f
) =
    "https://skyview.gsfc.nasa.gov/current/cgi/runquery.pl" +
    "?Position=$ra,$dec&Size=$sizeDeg&Pixels=1000&Rotation=$rotation" +
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
    var selectedTelescope  by remember { mutableStateOf(EquipmentRepository.defaultEquipment.telescope) }
    var selectedOptElem    by remember { mutableStateOf(EquipmentRepository.defaultEquipment.opticalElement) }
    var selectedCamera     by remember { mutableStateOf(EquipmentRepository.defaultEquipment.camera) }
    var scaling            by remember { mutableStateOf("Linear") }
    var imageSizeDeg       by remember {
        val (w, h) = computeFov(
            EquipmentRepository.defaultEquipment.telescope,
            EquipmentRepository.defaultEquipment.opticalElement,
            EquipmentRepository.defaultEquipment.camera
        )
        mutableStateOf((max(w, h) * 1.1).toFloat())
    }
    var imageBytes         by remember { mutableStateOf<ByteArray?>(null) }
    var displayedImageSize by remember { mutableStateOf(1.0) }
    var displayedCenterRa  by remember { mutableStateOf(skyObj.obj.ra) }
    var displayedCenterDec by remember { mutableStateOf(skyObj.obj.dec) }
    var displayedRotation  by remember { mutableStateOf(0f) }
    var isLoading          by remember { mutableStateOf(false) }
    var errorMsg           by remember { mutableStateOf<String?>(null) }

    // Interactive rectangle state — pixel offset from image center and rotation in degrees
    var rectOffsetX  by remember { mutableStateOf(0f) }
    var rectOffsetY  by remember { mutableStateOf(0f) }
    var rectRotation by remember { mutableStateOf(0f) }

    // Flip toggles — applied to image and canvas rendering
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }

    // Pixel size of the image Box for degree↔pixel conversion
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val httpClient = remember { HttpClient() }
    DisposableEffect(Unit) { onDispose { httpClient.close() } }

    val scope = rememberCoroutineScope()

    // Live FOV from current equipment (updates rectangle immediately on equipment change)
    val (fovW, fovH) = computeFov(selectedTelescope, selectedOptElem, selectedCamera)

    suspend fun fetchImage() {
        val scl     = scaling
        val imgSize = imageSizeDeg.toDouble()

        // Convert pixel offset to sky-coordinate offset
        val newRa: Double
        val newDec: Double
        if (canvasSize.width > 0 && displayedImageSize > 0) {
            val dxDeg = rectOffsetX * displayedImageSize / canvasSize.width
            val dyDeg = rectOffsetY * displayedImageSize / canvasSize.height
            // North up, East left: right = West (RA-), down = South (Dec-)
            val dec   = displayedCenterDec - dyDeg
            newDec    = dec
            newRa     = displayedCenterRa - dxDeg / cos(dec * PI / 180.0).coerceAtLeast(0.001)
        } else {
            newRa  = skyObj.obj.ra
            newDec = skyObj.obj.dec
        }
        val flipFactor = if (flipH xor flipV) -1f else 1f
        val rot = ((displayedRotation * flipFactor + rectRotation) % 360f + 360f) % 360f

        isLoading = true
        errorMsg  = null
        imageBytes = null
        imageBytes = try {
            httpClient.get(skyViewUrl(newRa, newDec, imgSize, scl, rot)).readRawBytes()
        } catch (e: Exception) {
            errorMsg = "Failed to load image: ${e.message}"
            null
        }
        if (imageBytes != null) {
            displayedImageSize = imgSize
            displayedCenterRa  = newRa
            displayedCenterDec = newDec
            displayedRotation  = rot
            // Rectangle is now centered in the new image
            rectOffsetX  = 0f
            rectOffsetY  = 0f
            rectRotation = 0f
        }
        isLoading = false
    }

    // Auto-load on first entry
    LaunchedEffect(Unit) { fetchImage() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Image — fixed, does not scroll
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = false) { _, pan, _, rotation ->
                        if (imageBytes != null) {
                            // Invert pan/rotation axes to match flipped image orientation
                            rectOffsetX  += pan.x * (if (flipH) -1f else 1f)
                            rectOffsetY  += pan.y * (if (flipV) -1f else 1f)
                            rectRotation += rotation * (if (flipH xor flipV) -1f else 1f)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (imageBytes != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (flipH) -1f else 1f
                            scaleY = if (flipV) -1f else 1f
                        }
                ) {
                AsyncImage(
                    model = imageBytes,
                    contentDescription = "Sky survey image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rw = (fovW / displayedImageSize * size.width).toFloat()
                        .coerceIn(0f, size.width)
                    val rh = (fovH / displayedImageSize * size.height).toFloat()
                        .coerceIn(0f, size.height)
                    withTransform({
                        translate(
                            left = size.width  / 2f + rectOffsetX,
                            top  = size.height / 2f + rectOffsetY
                        )
                        rotate(degrees = rectRotation, pivot = Offset.Zero)
                    }) {
                        drawRect(
                            color   = Color.Red,
                            topLeft = Offset(-rw / 2f, -rh / 2f),
                            size    = Size(rw, rh),
                            style   = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                } // end flip Box
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

        // Fixed strip — Update button left, hint + FOV right-aligned
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                imageSizeDeg = (max(fovW, fovH) * 1.1).toFloat()
                scope.launch { fetchImage() }
            }, enabled = !isLoading) {
                Text("Update")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "Drag to reposition  \u00B7  Two fingers to rotate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "FOV: ${fovW.fmt2()}° \u00D7 ${fovH.fmt2()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Controls — scrollable, takes remaining height
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
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
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Image Size",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(120.dp)
                )
                Slider(
                    value = imageSizeDeg,
                    onValueChange = { imageSizeDeg = it },
                    onValueChangeFinished = { if (!isLoading) scope.launch { fetchImage() } },
                    valueRange = 0.5f..5.0f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    " ${imageSizeDeg.toDouble().fmt2()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { flipH = !flipH }) {
                    Text("Flip Horizontal")
                }
                OutlinedButton(onClick = { flipV = !flipV }) {
                    Text("Flip Vertical")
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
