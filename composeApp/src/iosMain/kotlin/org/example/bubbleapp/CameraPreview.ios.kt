package org.example.bubbleapp

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.UIKitViewController
import platform.UIKit.UIViewController

@Composable
actual fun CameraPreview(
    modifier: Modifier,
    cameraController: CameraController,
    onPreviewReady: (() -> Unit)?
) {
    val previewController = remember(cameraController) {
        cameraController.createPreviewController() as UIViewController
    }

    var currentZoom by remember { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    currentZoom = (currentZoom * zoom).coerceIn(1f, 5f)
                    cameraController.setZoom(currentZoom)
                }
            }
    ) {
        UIKitViewController(
            modifier = Modifier.matchParentSize(),
            factory = { previewController }
        )
    }
}
