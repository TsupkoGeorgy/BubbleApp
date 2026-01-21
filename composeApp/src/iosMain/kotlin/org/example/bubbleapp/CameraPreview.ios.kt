package org.example.bubbleapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    UIKitViewController(
        modifier = modifier,
        factory = { previewController }
    )
}
