package org.example.bubbleapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import bubbleapp.composeapp.generated.resources.Res
import bubbleapp.composeapp.generated.resources.compose_multiplatform

@Composable
fun App() {
    MaterialTheme {
        val cameraController = rememberCameraController()
        var showCamera by remember { mutableStateOf(false) }

        var recordedVideoInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }
        var inlinePlayer by remember { mutableStateOf<InlineVideoPlayer?>(null) }

        LaunchedEffect(cameraController) {
            cameraController.onCameraReady = {
                cameraController.startRecording()
            }

            cameraController.onVideoRecorded = { name, size ->
                recordedVideoInfo = name to size
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                if (!showCamera) {
                    showCamera = true
                } else {
                    cameraController.stopRecording()
                    showCamera = false
                }
            }) {
                Text(if (showCamera) "Остановить запись" else "Открыть камеру")
            }

            if (showCamera) {
                Button(
                    onClick = { cameraController.switchCamera() }
                ) {
                    Text("🔄 Камера")
                }
                CameraPreview(
                    modifier = Modifier.size(300.dp).clip(CircleShape),
                    cameraController = cameraController,
                    onPreviewReady = {
                        cameraController.startCamera()
                    }
                )
            }
            recordedVideoInfo?.let { (name, size) ->
//                Column(
//                    horizontalAlignment = Alignment.CenterHorizontally,
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text("Файл: $name")
//                    Text("Размер: ${size / 1024} KB")
//
//                    Button(
//                        onClick = {
//                            cameraController.playVideo(name)
//                        }
//                    ) {
//                        Text("▶️ Проиграть")
//                    }
//                }




                recordedVideoInfo?.let { (name, size) ->

                    var isPlaying by remember { mutableStateOf(false) }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (inlinePlayer == null) {
                                    // создаем плеер
                                    inlinePlayer = InlineVideoPlayer(name)
                                    inlinePlayer?.play()
                                    isPlaying = true
                                } else {
                                    // переключаем состояние воспроизведения
                                    if (isPlaying) {
                                        inlinePlayer?.pause()
                                    } else {
                                        inlinePlayer?.play()
                                    }
                                    isPlaying = !isPlaying
                                }
                            }
                        ) {
                            Text(if (isPlaying) "⏸ Остановить" else "▶️ Проиграть")
                        }

                        inlinePlayer?.let { player ->
                            InlineVideoPlayerView(
                                modifier = Modifier
                                    .size(250.dp)
                                    .clip(CircleShape),
                                player = player
                            )
                        }
                    }
                }

            }
        }
    }
}


// commonMain
interface CameraController {
    fun startCamera()
    fun stopCamera()
    fun startRecording()
    fun stopRecording()
    fun switchCamera()

    fun playVideo(fileName: String)
    // Callback при завершении записи
    fun createInlinePlayer(fileName: String): Any

    var onVideoRecorded: ((fileName: String, fileSize: Long) -> Unit)?
    var onCameraReady: (() -> Unit)?

}

@Composable
expect fun rememberCameraController(): CameraController


@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraController: CameraController,
    onPreviewReady: (() -> Unit)? = null
)


@Composable
expect fun InlineVideoPlayerView(
    modifier: Modifier = Modifier,
    player: InlineVideoPlayer
)

expect class InlineVideoPlayer(fileName: String) {
    fun play()
    fun pause()
}
