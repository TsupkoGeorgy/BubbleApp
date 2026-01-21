package org.example.bubbleapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    MaterialTheme {
        val cameraController = rememberCameraController()
        var showCamera by remember { mutableStateOf(false) }
        var recordedVideos by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedVideo by remember { mutableStateOf<String?>(null) }
        var inlinePlayer by remember { mutableStateOf<InlineVideoPlayer?>(null) }
        var isPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(cameraController) {
            cameraController.onCameraReady = {
                cameraController.startRecording()
            }

            cameraController.onVideoRecorded = { _, _ ->
                recordedVideos = cameraController.getRecordedVideos()
            }
        }

        LaunchedEffect(Unit) {
            recordedVideos = cameraController.getRecordedVideos()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                // Верхняя часть - кнопка записи и камера
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                        Text(if (showCamera) "Остановить" else "Записать")
                    }

                    if (showCamera) {
                        Button(
                            onClick = { cameraController.switchCamera() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Повернуть")
                        }
                        CameraPreview(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(200.dp)
                                .clip(CircleShape),
                            cameraController = cameraController,
                            onPreviewReady = {
                                cameraController.startCamera()
                            }
                        )
                    }
                }

                // Нижняя часть - грид с записанными кружками
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = "Записи (${recordedVideos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (recordedVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Нет записей")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recordedVideos) { videoName ->
                                VideoCircleItem(
                                    videoName = videoName,
                                    isSelected = videoName == selectedVideo,
                                    onClick = {
                                        if (selectedVideo == videoName) {
                                            // Закрыть
                                            inlinePlayer?.pause()
                                            inlinePlayer = null
                                            selectedVideo = null
                                            isPlaying = false
                                        } else {
                                            // Новое видео
                                            inlinePlayer?.pause()
                                            inlinePlayer = InlineVideoPlayer(videoName)
                                            inlinePlayer?.play()
                                            selectedVideo = videoName
                                            isPlaying = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Оверлей с видео по центру экрана (как в Telegram)
            selectedVideo?.let { _ ->
                inlinePlayer?.let { player ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x80000000))
                            .clickable {
                                inlinePlayer?.pause()
                                inlinePlayer = null
                                selectedVideo = null
                                isPlaying = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        InlineVideoPlayerView(
                            modifier = Modifier
                                .size(250.dp)
                                .clip(CircleShape)
                                .clickable {
                                    // Toggle play/pause
                                    if (isPlaying) {
                                        inlinePlayer?.pause()
                                        isPlaying = false
                                    } else {
                                        inlinePlayer?.play()
                                        isPlaying = true
                                    }
                                },
                            player = player
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCircleItem(
    videoName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        VideoThumbnail(
            fileName = videoName,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x806200EE))
            )
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
    fun createInlinePlayer(fileName: String): Any
    fun getRecordedVideos(): List<String>
    fun createPreviewController(): Any

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

@Composable
expect fun VideoThumbnail(
    fileName: String,
    modifier: Modifier = Modifier
)
