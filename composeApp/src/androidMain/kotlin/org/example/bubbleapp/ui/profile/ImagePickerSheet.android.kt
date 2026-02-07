package org.example.bubbleapp.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun ImagePickerSheet(
    onDismiss: () -> Unit,
    onImageSelected: (ByteArray, String) -> Unit
) {
    val context = LocalContext.current
    var showSourceSelection by remember { mutableStateOf(true) }
    var selectedSource by remember { mutableStateOf<ImageSource?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: byteArrayOf()
                inputStream?.close()
                val fileName = "avatar_${System.currentTimeMillis()}.jpg"
                onImageSelected(bytes, fileName)
            } catch (e: Exception) {
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(tempPhotoUri!!)
                val bytes = inputStream?.readBytes() ?: byteArrayOf()
                inputStream?.close()
                val fileName = "avatar_${System.currentTimeMillis()}.jpg"
                onImageSelected(bytes, fileName)
            } catch (e: Exception) {
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Create temp file and launch camera
            val photoFile = File.createTempFile(
                "avatar_${System.currentTimeMillis()}",
                ".jpg",
                context.cacheDir
            )
            tempPhotoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(tempPhotoUri!!)
        } else {
            onDismiss()
        }
    }

    // Launch picker based on selected source
    LaunchedEffect(selectedSource) {
        when (selectedSource) {
            ImageSource.Gallery -> {
                galleryLauncher.launch("image/*")
            }
            ImageSource.Camera -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val photoFile = File.createTempFile(
                        "avatar_${System.currentTimeMillis()}",
                        ".jpg",
                        context.cacheDir
                    )
                    tempPhotoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    cameraLauncher.launch(tempPhotoUri!!)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            null -> {}
        }
    }

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
                    .clickable(enabled = false) { }
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
    }
}

private enum class ImageSource {
    Gallery,
    Camera
}
