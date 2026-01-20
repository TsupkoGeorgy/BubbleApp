package org.example.bubbleapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    cameraController: CameraController,
    onPreviewReady: (() -> Unit)?
) {
    val iosController = cameraController as IOSCameraController

    UIKitViewController(
        modifier = modifier,
        factory = {
            CameraViewController(iosController)
        }
    )
}

class CameraViewController(
    private val controller: IOSCameraController
) : UIViewController(null, null) {

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.clearColor
        view.layer.addSublayer(controller.previewLayer)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        controller.previewLayer.frame = view.bounds
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        controller.startCamera()
    }
}
