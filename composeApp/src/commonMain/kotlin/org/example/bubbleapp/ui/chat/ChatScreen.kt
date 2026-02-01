package org.example.bubbleapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.bubbleapp.CameraController
import org.example.bubbleapp.CameraPreview
import org.example.bubbleapp.VideoPreviewPlayer
import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.rememberCameraController

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onCall: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val cameraController = rememberCameraController()

    // Setup camera callbacks
    LaunchedEffect(cameraController) {
        cameraController.onCameraReady = {
            cameraController.startRecording()
        }
        cameraController.onVideoRecorded = { fileName, filePath, fileSize ->
            viewModel.onVideoRecorded(fileName, filePath, fileSize)
        }
    }

    // Подписка на события
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.MessageSent, is ChatEvent.VideoBubbleSent -> {
                    // Scroll to bottom on new message
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }
                is ChatEvent.Error -> {
                    // TODO: Show snackbar
                }
            }
        }
    }

    // Mark as read when screen opens
    LaunchedEffect(state.messages) {
        if (state.messages.isNotEmpty()) {
            viewModel.markAsRead()
        }
    }

    // Load more when scrolling to top (end of reversed list)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= state.messages.size - 5 &&
                    !state.isLoadingMore &&
                    state.hasMoreMessages
                ) {
                    viewModel.loadMoreMessages()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // Header
        ChatHeader(
            chatName = state.chat?.name ?: "Чат",
            isOnline = state.isOnline,
            isTyping = state.isTyping,
            typingUserName = state.typingUserName,
            lastSeen = state.lastSeen,
            onBack = onBack,
            onCall = onCall
        )

        // Messages
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF6C63FF)
                    )
                }

                state.errorMessage != null && state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.errorMessage ?: "Ошибка",
                            color = Color(0xFFFF6B6B),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadMessages() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6C63FF)
                            )
                        ) {
                            Text("Повторить")
                        }
                    }
                }

                state.messages.isEmpty() -> {
                    Text(
                        text = "Нет сообщений\nНапишите первое!",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        reverseLayout = true // Latest messages at bottom
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                isOwn = viewModel.isOwnMessage(message),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF6C63FF),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Input field
        MessageInput(
            text = state.messageText,
            onTextChange = { viewModel.onMessageTextChanged(it) },
            onSend = { viewModel.sendMessage() },
            onRecordBubble = { viewModel.startRecording() },
            isSending = state.isSending,
            isUploading = state.isUploading
        )

        // Recording overlay
        if (state.isRecording) {
            RecordingOverlay(
                cameraController = cameraController,
                onCancel = {
                    cameraController.stopRecording()
                    viewModel.cancelRecording()
                },
                onStop = { cameraController.stopRecording() }
            )
        }

        // Uploading indicator
        if (state.isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Отправка...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    chatName: String,
    isOnline: Boolean,
    isTyping: Boolean,
    typingUserName: String?,
    lastSeen: String?,
    onBack: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2a2a4e))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "←",
            fontSize = 24.sp,
            color = Color.White,
            modifier = Modifier
                .clickable { onBack() }
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Avatar with online indicator
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C63FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chatName.firstOrNull()?.toString()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            // Online indicator
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF00C853))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            // Status line: typing > online > last seen
            Text(
                text = when {
                    isTyping -> typingUserName?.let { "$it печатает..." } ?: "печатает..."
                    isOnline -> "в сети"
                    lastSeen != null -> "был(а) ${formatLastSeen(lastSeen)}"
                    else -> ""
                },
                fontSize = 13.sp,
                color = when {
                    isTyping -> Color(0xFF00C9A7)
                    isOnline -> Color(0xFF00C853)
                    else -> Color.White.copy(alpha = 0.5f)
                }
            )
        }

        // Call button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF00C9A7))
                .clickable { onCall() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📞",
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isOwn) Color(0xFF6C63FF) else Color(0xFF2a2a4e)
    val alignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            // Show sender name for group chats
            message.sender?.let { sender ->
                if (!isOwn) {
                    Text(
                        text = sender.displayName ?: sender.username ?: sender.phone,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6C63FF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Message content
            when (message.type) {
                "TEXT" -> {
                    Text(
                        text = message.content ?: "",
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                "VIDEO_BUBBLE" -> {
                    VideoBubbleContent(message = message)
                }
                "VOICE" -> {
                    Text(
                        text = "[Голосовое сообщение]",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                else -> {
                    Text(
                        text = message.content ?: "[Сообщение]",
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }

            // Time and status
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMessageTime(message.createdAt),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                if (isOwn) {
                    MessageStatus(status = message.status)
                }
            }
        }
    }
}

@Composable
private fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onRecordBubble: () -> Unit,
    isSending: Boolean,
    isUploading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2a2a4e))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Record bubble button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF00C9A7))
                .clickable(enabled = !isSending && !isUploading) { onRecordBubble() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "●",
                fontSize = 24.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Сообщение...", color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6C63FF),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color.White,
                focusedContainerColor = Color(0xFF1a1a2e),
                unfocusedContainerColor = Color(0xFF1a1a2e)
            ),
            maxLines = 4
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Send button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank() && !isSending)
                        Color(0xFF6C63FF)
                    else
                        Color.Gray.copy(alpha = 0.5f)
                )
                .clickable(enabled = text.isNotBlank() && !isSending) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "→",
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun RecordingOverlay(
    cameraController: CameraController,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera preview in circle
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    cameraController = cameraController,
                    onPreviewReady = {
                        cameraController.startCamera()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // Cancel button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 28.sp,
                        color = Color.White
                    )
                }

                // Stop/Send button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF4444))
                        .clickable { onStop() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "■",
                        fontSize = 28.sp,
                        color = Color.White
                    )
                }

                // Switch camera
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6C63FF))
                        .clickable { cameraController.switchCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻",
                        fontSize = 28.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Записываем кружок...",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun VideoBubbleContent(message: Message) {
    var isPlaying by remember { mutableStateOf(false) }

    // Get video URL from attachment or content
    val videoSource = message.attachments.firstOrNull()?.url
        ?: message.content // Fallback to local filename

    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .clickable { isPlaying = !isPlaying },
        contentAlignment = Alignment.Center
    ) {
        if (videoSource != null) {
            VideoPreviewPlayer(
                fileName = videoSource,
                modifier = Modifier.fillMaxSize()
            )

            // Play icon overlay when not playing
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 20.sp,
                            color = Color(0xFF6C63FF)
                        )
                    }
                }
            }
        } else {
            // Placeholder if no video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "●",
                    fontSize = 48.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatMessageTime(isoDate: String): String {
    return try {
        val timePart = isoDate.substringAfter("T").substringBefore(".")
        timePart.substring(0, 5) // "HH:mm"
    } catch (e: Exception) {
        ""
    }
}

private fun formatLastSeen(isoDate: String): String {
    return try {
        val timePart = isoDate.substringAfter("T").substringBefore(".")
        "в ${timePart.substring(0, 5)}"
    } catch (e: Exception) {
        "недавно"
    }
}

@Composable
private fun MessageStatus(status: String) {
    val (icon, color) = when (status.uppercase()) {
        "SENDING" -> "◷" to Color.White.copy(alpha = 0.3f)
        "SENT" -> "✓" to Color.White.copy(alpha = 0.5f)
        "DELIVERED" -> "✓✓" to Color.White.copy(alpha = 0.5f)
        "READ" -> "✓✓" to Color(0xFF00C9A7)
        "FAILED" -> "✕" to Color(0xFFFF6B6B)
        else -> "✓" to Color.White.copy(alpha = 0.5f)
    }
    Text(
        text = icon,
        fontSize = 11.sp,
        color = color
    )
}
