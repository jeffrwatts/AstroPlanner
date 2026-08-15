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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

private fun computeFov(config: EquipmentConfig): Pair<Double, Double> {
    val fl      = config.focalLength * config.focalReducerFactor
    val sensorW = config.pixelSize * config.resolutionWidth  / 1000.0  // µm → mm
    val sensorH = config.pixelSize * config.resolutionHeight / 1000.0
    val fovW    = 2.0 * atan(sensorW / (2.0 * fl)) * (180.0 / PI)
    val fovH    = 2.0 * atan(sensorH / (2.0 * fl)) * (180.0 / PI)
    return Pair(fovW, fovH)
}

private fun plateScale(config: EquipmentConfig): Double {
    val fl = config.focalLength * config.focalReducerFactor
    return (config.pixelSize / fl) * 206.265   // arcsec/pixel
}

private fun skyViewUrl(
    ra: Double, dec: Double, sizeDeg: Double, scaling: String, rotation: Float = 0f
) =
    "https://skyview.gsfc.nasa.gov/current/cgi/runquery.pl" +
    "?Position=$ra,$dec&Size=$sizeDeg&Pixels=1000&Rotation=$rotation" +
    "&Scaling=$scaling&Return=PNG&coordinates=J2000&Survey=DSS"

// Pixel offset (from canvas center, pre-flip) at which a given sky position renders,
// given the currently displayed image's center/size/rotation. Mirrors (inverts) the
// pixel-offset -> RA/Dec math in fetchImage()/currentPointing() above.
private fun skyToCanvasOffset(
    targetRa: Double, targetDec: Double,
    centerRa: Double, centerDec: Double,
    imageSizeDeg: Double, rotationDeg: Float,
    canvasWidth: Float, canvasHeight: Float
): Offset {
    val dxDeg = (centerRa - targetRa) * cos(centerDec * PI / 180.0)
    val dyDeg = centerDec - targetDec
    val dxSky = dxDeg * canvasWidth  / imageSizeDeg
    val dySky = dyDeg * canvasHeight / imageSizeDeg
    val rotRad = rotationDeg * PI / 180.0
    val cosR = cos(rotRad)
    val sinR = sin(rotRad)
    // Inverse of forward rotation dxSky = x*cosR - y*sinR, dySky = x*sinR + y*cosR
    val x = (dxSky * cosR + dySky * sinR).toFloat()
    val y = (-dxSky * sinR + dySky * cosR).toFloat()
    return Offset(x, y)
}

private val TargetMarkerColor = Color(0xFF40E0FF) // cyan — distinct from the red FOV rectangle
private val CompStarMarkerColor = Color(0xFFFFD740) // yellow — distinct from the target marker

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
    equipmentRepository: EquipmentRepository,
    onBack: () -> Unit,
    comparisonStars: List<ComparisonStar> = emptyList(),
    onPickCoordinates: ((ra: Double, dec: Double) -> Unit)? = null
) {
    val configs            = remember { equipmentRepository.getAll() }
    var selectedConfig     by remember { mutableStateOf(configs.firstOrNull()) }
    var scaling            by remember { mutableStateOf("Linear") }
    var imageSizeDeg       by remember {
        val (w, h) = selectedConfig?.let { computeFov(it) } ?: Pair(1.0, 1.0)
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

    val textMeasurer = rememberTextMeasurer()

    val httpClient = remember { HttpClient() }
    DisposableEffect(Unit) { onDispose { httpClient.close() } }

    val scope = rememberCoroutineScope()

    // Live FOV from current equipment (updates rectangle immediately on equipment change)
    val (fovW, fovH) = selectedConfig?.let { computeFov(it) } ?: Pair(0.0, 0.0)

    suspend fun fetchImage() {
        val scl     = scaling
        val imgSize = imageSizeDeg.toDouble()

        // Convert pixel offset to sky-coordinate offset
        val newRa: Double
        val newDec: Double
        if (canvasSize.width > 0 && displayedImageSize > 0) {
            // Un-rotate pixel offset into sky frame to account for image rotation.
            // The SkyView image is rotated by displayedRotation degrees, so screen axes
            // are misaligned with sky axes by that amount. Apply the inverse rotation
            // to recover the sky-frame (North-up, East-left) offset.
            val rotRad = displayedRotation * PI / 180.0
            val cosR   = cos(rotRad)
            val sinR   = sin(rotRad)
            val dxSky  = rectOffsetX * cosR - rectOffsetY * sinR
            val dySky  = rectOffsetX * sinR + rectOffsetY * cosR
            val dxDeg  = dxSky * displayedImageSize / canvasSize.width
            val dyDeg  = dySky * displayedImageSize / canvasSize.height
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

    // Compute the sky coordinates currently under the center of the FOV rectangle,
    // accounting for any pan offset and image rotation. Mirrors the math in fetchImage().
    fun currentPointing(): Pair<Double, Double> {
        if (canvasSize.width == 0 || displayedImageSize == 0.0) {
            return Pair(displayedCenterRa, displayedCenterDec)
        }
        val rotRad = displayedRotation * PI / 180.0
        val cosR   = cos(rotRad); val sinR = sin(rotRad)
        val dxSky  = rectOffsetX * cosR - rectOffsetY * sinR
        val dySky  = rectOffsetX * sinR + rectOffsetY * cosR
        val dxDeg  = dxSky * displayedImageSize / canvasSize.width
        val dyDeg  = dySky * displayedImageSize / canvasSize.height
        val dec    = displayedCenterDec - dyDeg
        val ra     = displayedCenterRa - dxDeg / cos(dec * PI / 180.0).coerceAtLeast(0.001)
        return Pair(ra, dec)
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

                    // Mark the variable star's actual sky position. The image can be
                    // panned/rotated/resized independently (via drag or "Update"), so this
                    // is recomputed from displayedCenter*/displayedRotation every draw —
                    // it never assumes the star is still centered. Only meaningful for
                    // variable stars/standard fields — other object types fill the frame
                    // by design, so a target marker would just be clutter.
                    val isVariableOrStandardField = skyObj.obj.type == ObjectType.VARIABLE_STAR ||
                        skyObj.obj.type == ObjectType.STANDARD_FIELD
                    if (isVariableOrStandardField && displayedImageSize > 0.0) {
                        val markerOffset = skyToCanvasOffset(
                            targetRa      = skyObj.obj.ra,
                            targetDec     = skyObj.obj.dec,
                            centerRa      = displayedCenterRa,
                            centerDec     = displayedCenterDec,
                            imageSizeDeg  = displayedImageSize,
                            rotationDeg   = displayedRotation,
                            canvasWidth   = size.width,
                            canvasHeight  = size.height
                        )
                        val cx = size.width  / 2f + markerOffset.x
                        val cy = size.height / 2f + markerOffset.y
                        val labelStyle = TextStyle(fontSize = 12.sp, color = TargetMarkerColor)
                        val label = skyObj.obj.displayName
                        val measured = textMeasurer.measure(label, labelStyle)
                        val onScreenMargin = 18.dp.toPx()

                        if (cx >= -onScreenMargin && cx <= size.width + onScreenMargin &&
                            cy >= -onScreenMargin && cy <= size.height + onScreenMargin
                        ) {
                            // Target is within (or just at the edge of) the frame — circle + label.
                            val radius = 16.dp.toPx()
                            drawCircle(
                                color  = TargetMarkerColor,
                                radius = radius,
                                center = Offset(cx, cy),
                                style  = Stroke(width = 2.dp.toPx())
                            )
                            val labelX = (cx - measured.size.width / 2f)
                                .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                            val labelY = (cy + radius + 4.dp.toPx())
                                .coerceIn(0f, (size.height - measured.size.height).coerceAtLeast(0f))
                            drawText(textMeasurer = textMeasurer, text = label, topLeft = Offset(labelX, labelY), style = labelStyle)
                        } else {
                            // Panned out of frame — point an arrow at the frame edge toward it
                            // instead of silently omitting the marker.
                            val dx = cx - size.width / 2f
                            val dy = cy - size.height / 2f
                            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            val edgeInset = 24.dp.toPx()
                            val ex = (size.width  / 2f + dx / dist * (size.width  / 2f - edgeInset))
                                .coerceIn(edgeInset, size.width  - edgeInset)
                            val ey = (size.height / 2f + dy / dist * (size.height / 2f - edgeInset))
                                .coerceIn(edgeInset, size.height - edgeInset)
                            val angleDeg = (atan2(dy, dx) * 180.0 / PI).toFloat()
                            withTransform({
                                translate(left = ex, top = ey)
                                rotate(degrees = angleDeg, pivot = Offset.Zero)
                            }) {
                                val arrow = Path().apply {
                                    moveTo(10.dp.toPx(), 0f)
                                    lineTo(-6.dp.toPx(), -6.dp.toPx())
                                    lineTo(-6.dp.toPx(), 6.dp.toPx())
                                    close()
                                }
                                drawPath(arrow, color = TargetMarkerColor)
                            }
                            val offscreenLabel = "$label (outside field)"
                            val measuredOffscreen = textMeasurer.measure(offscreenLabel, labelStyle)
                            val labelX = (ex - measuredOffscreen.size.width / 2f)
                                .coerceIn(0f, (size.width - measuredOffscreen.size.width).coerceAtLeast(0f))
                            val labelY = (ey + 12.dp.toPx())
                                .coerceIn(0f, (size.height - measuredOffscreen.size.height).coerceAtLeast(0f))
                            drawText(textMeasurer = textMeasurer, text = offscreenLabel, topLeft = Offset(labelX, labelY), style = labelStyle)
                        }
                    }

                    // AAVSO comparison stars, labeled with their chart label. The cache
                    // covers a 2° window — much wider than any real frame — so most are
                    // expected to fall outside the current view; only draw the ones that
                    // land inside it rather than cluttering the frame with off-screen arrows.
                    if (displayedImageSize > 0.0 && comparisonStars.isNotEmpty()) {
                        val compLabelStyle = TextStyle(fontSize = 10.sp, color = CompStarMarkerColor)
                        val compRadius = 8.dp.toPx()
                        val compMargin = 4.dp.toPx()
                        comparisonStars.forEach { comp ->
                            val offset = skyToCanvasOffset(
                                targetRa      = comp.ra,
                                targetDec     = comp.dec,
                                centerRa      = displayedCenterRa,
                                centerDec     = displayedCenterDec,
                                imageSizeDeg  = displayedImageSize,
                                rotationDeg   = displayedRotation,
                                canvasWidth   = size.width,
                                canvasHeight  = size.height
                            )
                            val cx = size.width  / 2f + offset.x
                            val cy = size.height / 2f + offset.y
                            if (cx >= compRadius - compMargin && cx <= size.width  - compRadius + compMargin &&
                                cy >= compRadius - compMargin && cy <= size.height - compRadius + compMargin
                            ) {
                                drawCircle(
                                    color  = CompStarMarkerColor,
                                    radius = compRadius,
                                    center = Offset(cx, cy),
                                    style  = Stroke(width = 1.5.dp.toPx())
                                )
                                val measuredComp = textMeasurer.measure(comp.label, compLabelStyle)
                                val labelX = (cx - measuredComp.size.width / 2f)
                                    .coerceIn(0f, (size.width - measuredComp.size.width).coerceAtLeast(0f))
                                val labelY = (cy + compRadius + 2.dp.toPx())
                                    .coerceIn(0f, (size.height - measuredComp.size.height).coerceAtLeast(0f))
                                drawText(textMeasurer = textMeasurer, text = comp.label, topLeft = Offset(labelX, labelY), style = compLabelStyle)
                            }
                        }
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
            if (onPickCoordinates != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val (ra, dec) = currentPointing()
                        onPickCoordinates(ra, dec)
                        onBack()
                    },
                    enabled = !isLoading && imageBytes != null
                ) {
                    Text("Apply")
                }
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
                selectedConfig?.let {
                    Text(
                        "Scale: ${plateScale(it).fmt2()}\"/px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "RA: ${displayedCenterRa.formatRa()}  Dec: ${displayedCenterDec.formatDec()}",
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
            if (configs.isEmpty()) {
                Text(
                    "No equipment configured — add one in Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                EquipmentRow(
                    label = "Equipment",
                    options = configs,
                    selected = selectedConfig ?: configs.first(),
                    displayName = { it.name },
                    onSelect = { selectedConfig = it }
                )
            }
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
