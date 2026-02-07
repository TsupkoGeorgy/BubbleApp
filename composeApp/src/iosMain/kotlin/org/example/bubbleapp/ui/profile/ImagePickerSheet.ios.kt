package org.example.bubbleapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraDevice
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGRectMake
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import org.example.bubbleapp.data.auth.currentTimeMillis

// Keep strong reference to delegates to prevent GC
private var photoPickerDelegate: NSObject? = null
private var cameraPickerDelegate: NSObject? = null

@Composable
actual fun ImagePickerSheet(
    onDismiss: () -> Unit,
    onImageSelected: (ByteArray, String) -> Unit
) {
    var showSourceSelection by remember { mutableStateOf(true) }
    var selectedSource by remember { mutableStateOf<ImageSource?>(null) }

    if (showSourceSelection) {
        // Bottom sheet with source selection
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF2a2a4e),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .clickable(enabled = false) { } // Prevent click-through
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Выберите источник",
                    fontSize = 18.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        showSourceSelection = false
                        selectedSource = ImageSource.Gallery
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C63FF)
                    )
                ) {
                    Text("Галерея", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        showSourceSelection = false
                        selectedSource = ImageSource.Camera
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C9A7)
                    )
                ) {
                    Text("Камера", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Отмена",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else {
        // Launch picker based on selected source
        selectedSource?.let { source ->
            when (source) {
                ImageSource.Gallery -> {
                    PhotoPickerLauncher(
                        onImageSelected = { data, fileName ->
                            onImageSelected(data, fileName)
                        },
                        onDismiss = onDismiss
                    )
                }
                ImageSource.Camera -> {
                    CameraPickerLauncher(
                        onImageSelected = { data, fileName ->
                            onImageSelected(data, fileName)
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

private enum class ImageSource {
    Gallery,
    Camera
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
private fun PhotoPickerLauncher(
    onImageSelected: (ByteArray, String) -> Unit,
    onDismiss: () -> Unit
) {
    DisposableEffect(Unit) {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
            selection = PHPickerConfigurationSelectionOrdered
        }

        val picker = PHPickerViewController(configuration)

        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)

                val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                if (results.isEmpty()) {
                    dispatch_async(dispatch_get_main_queue()) {
                        onDismiss()
                    }
                    return
                }

                val result = results.first()
                result.itemProvider.loadDataRepresentationForTypeIdentifier(
                    "public.image"
                ) { data, error ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (data != null && error == null) {
                            val image = UIImage.imageWithData(data)
                            if (image != null) {
                                val normalizedImage = image.normalizeOrientation()
                                val jpegData = UIImageJPEGRepresentation(normalizedImage, 0.8)
                                if (jpegData != null) {
                                    val byteArray = jpegData.toByteArray()
                                    val fileName = "avatar_${currentTimeMillis()}.jpg"
                                    onImageSelected(byteArray, fileName)
                                } else {
                                    onDismiss()
                                }
                            } else {
                                onDismiss()
                            }
                        } else {
                            onDismiss()
                        }
                    }
                }
            }
        }

        // Keep strong reference to prevent GC
        photoPickerDelegate = delegate
        picker.delegate = delegate

        val rootController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootController?.presentViewController(picker, true, null)

        onDispose {
            photoPickerDelegate = null
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun CameraPickerLauncher(
    onImageSelected: (ByteArray, String) -> Unit,
    onDismiss: () -> Unit
) {
    DisposableEffect(Unit) {
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            onDismiss()
            return@DisposableEffect onDispose { }
        }

        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.allowsEditing = true
        // Use front camera for selfie/avatar
        picker.cameraDevice = UIImagePickerControllerCameraDevice.UIImagePickerControllerCameraDeviceFront

        val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>
            ) {
                picker.dismissViewControllerAnimated(true, null)

                val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                    ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage

                dispatch_async(dispatch_get_main_queue()) {
                    if (image != null) {
                        val normalizedImage = image.normalizeOrientation()
                        val jpegData = UIImageJPEGRepresentation(normalizedImage, 0.8)
                        if (jpegData != null) {
                            val byteArray = jpegData.toByteArray()
                            val fileName = "avatar_${currentTimeMillis()}.jpg"
                            onImageSelected(byteArray, fileName)
                        } else {
                            onDismiss()
                        }
                    } else {
                        onDismiss()
                    }
                }
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
                dispatch_async(dispatch_get_main_queue()) {
                    onDismiss()
                }
            }
        }

        // Keep strong reference to prevent GC
        cameraPickerDelegate = delegate
        picker.delegate = delegate

        val rootController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootController?.presentViewController(picker, true, null)

        onDispose {
            cameraPickerDelegate = null
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val byteArray = ByteArray(length)
    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return byteArray
}

/**
 * Normalize image orientation to prevent rotation issues.
 * Images from camera can have different orientations that need to be fixed.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.normalizeOrientation(): UIImage {
    if (this.imageOrientation == UIImageOrientation.UIImageOrientationUp) {
        return this
    }

    val imageSize = this.size
    val width = imageSize.useContents { this.width }
    val height = imageSize.useContents { this.height }

    UIGraphicsBeginImageContextWithOptions(imageSize, false, this.scale)
    this.drawInRect(CGRectMake(0.0, 0.0, width, height))
    val normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return normalizedImage ?: this
}
