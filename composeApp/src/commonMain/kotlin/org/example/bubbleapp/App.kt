package org.example.bubbleapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2

// Состояние транскрипции
enum class TranscriptionState {
    Idle,
    Loading,
    Success,
    Error
}

data class TranscriptionResult(
    val state: TranscriptionState,
    val text: String? = null,
    val error: String? = null
)

// expect для транскрибации
expect fun transcribeVideo(fileName: String, onResult: (TranscriptionResult) -> Unit)
expect fun getCachedTranscription(fileName: String): String?

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
                                .size(300.dp)
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
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

            // Оверлей с видео по центру экрана
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
                        CircularVideoPlayer(
                            player = player,
                            isPlaying = isPlaying,
                            onTogglePlay = {
                                if (isPlaying) {
                                    inlinePlayer?.pause()
                                    isPlaying = false
                                } else {
                                    inlinePlayer?.play()
                                    isPlaying = true
                                }
                            }
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
    var transcriptionState by remember { mutableStateOf(TranscriptionState.Idle) }
    var transcriptionText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Проверяем кэш при первом рендере
    LaunchedEffect(videoName) {
        getCachedTranscription(videoName)?.let { cached ->
            transcriptionText = cached
            transcriptionState = TranscriptionState.Success
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Видео-кружок
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            VideoPreviewPlayer(
                fileName = videoName,
                modifier = Modifier.fillMaxSize()
            )
            // Прозрачный слой для перехвата нажатий поверх видео
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick() }
                    .background(if (isSelected) Color(0x806200EE) else Color.Transparent)
            )
        }

        // Кнопка транскрибации (показываем если ещё нет текста)
        if (transcriptionState == TranscriptionState.Idle) {
            Text(
                text = "Aa",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        transcriptionState = TranscriptionState.Loading
                        transcribeVideo(videoName) { result ->
                            transcriptionState = result.state
                            transcriptionText = result.text
                            errorText = result.error
                        }
                    }
            )
        }

        // Индикатор загрузки
        if (transcriptionState == TranscriptionState.Loading) {
            Text(
                text = "...",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Текст транскрипции
        if (transcriptionState == TranscriptionState.Success && transcriptionText != null) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.9f)
                    .background(
                        color = Color(0xFFF5F5F5),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = transcriptionText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
        }

        // Ошибка
        if (transcriptionState == TranscriptionState.Error && errorText != null) {
            Text(
                text = errorText!!,
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun CircularVideoPlayer(
    player: InlineVideoPlayer,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val videoSize = 250.dp
    val seekBarWidth = 6.dp
    val totalSize = videoSize + seekBarWidth * 2 + 8.dp

    var progress by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Обновление прогресса во время воспроизведения
    LaunchedEffect(isPlaying, isDragging) {
        while (isPlaying && !isDragging) {
            val duration = player.getDuration()
            if (duration > 0) {
                progress = (player.getCurrentTime() / duration).toFloat().coerceIn(0f, 1f)
            }
            kotlinx.coroutines.delay(100)
        }
    }

    Box(
        modifier = Modifier
            .size(totalSize)
            .clickable(enabled = false) { }, // Блокируем клик на фон
        contentAlignment = Alignment.Center
    ) {
        // Круговой seek bar
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val center = Offset(size.width / 2f, size.height / 2f)
                            progress = offsetToProgress(offset, center)
                            val duration = player.getDuration()
                            if (duration > 0) {
                                player.seekTo(progress.toDouble() * duration)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            progress = offsetToProgress(change.position, center)
                            val duration = player.getDuration()
                            if (duration > 0) {
                                player.seekTo(progress.toDouble() * duration)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val strokeWidth = seekBarWidth.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )
            val arcSize = Size(radius * 2, radius * 2)

            // Фоновая дорожка
            drawArc(
                color = Color.White.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Прогресс
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Видео в центре
        InlineVideoPlayerView(
            modifier = Modifier
                .size(videoSize)
                .clip(CircleShape)
                .clickable { onTogglePlay() },
            player = player
        )
    }
}

// Конвертация позиции касания в прогресс (0-1), начало сверху, по часовой
private fun offsetToProgress(offset: Offset, center: Offset): Float {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    // atan2 возвращает угол от -PI до PI, 0 справа
    // Нам нужно: 0 сверху, по часовой стрелке
    var angle = atan2(dx.toDouble(), -dy.toDouble()) // -dy чтобы 0 был сверху
    if (angle < 0) angle += 2 * PI
    return (angle / (2 * PI)).toFloat()
}

// commonMain
interface CameraController {
    fun startCamera()
    fun stopCamera()
    fun startRecording()
    fun stopRecording()
    fun switchCamera()
    fun setZoom(factor: Float)

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
    fun getDuration(): Double
    fun getCurrentTime(): Double
    fun seekTo(seconds: Double)
}

@Composable
expect fun VideoThumbnail(
    fileName: String,
    modifier: Modifier = Modifier
)

@Composable
expect fun VideoPreviewPlayer(
    fileName: String,
    modifier: Modifier = Modifier
)
