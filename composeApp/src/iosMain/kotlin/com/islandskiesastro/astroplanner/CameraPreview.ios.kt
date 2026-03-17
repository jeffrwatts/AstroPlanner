@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.islandskiesastro.astroplanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMediaTypeVideo
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

@Composable
actual fun CameraPreview(modifier: Modifier) {
    UIKitView(
        factory = {
            val view = UIView()
            val session = AVCaptureSession().apply {
                sessionPreset = AVCaptureSessionPresetHigh
                val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
                if (device != null) {
                    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                    if (input != null && canAddInput(input)) {
                        addInput(input)
                    }
                }
                startRunning()
            }
            val previewLayer = AVCaptureVideoPreviewLayer.layerWithSession(session)
            CATransaction.begin()
            CATransaction.setValue(true, kCATransactionDisableActions)
            view.layer.addSublayer(previewLayer)
            CATransaction.commit()
            view
        },
        update = { view ->
            val layer = view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer
            layer?.frame = view.bounds
        },
        modifier = modifier
    )
}
