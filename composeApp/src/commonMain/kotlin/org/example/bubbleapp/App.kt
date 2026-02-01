package org.example.bubbleapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.bubbleapp.call.CallManager
import org.example.bubbleapp.call.CallState
import org.example.bubbleapp.data.auth.AuthState
import org.example.bubbleapp.ui.auth.PhoneInputScreen
import org.example.bubbleapp.ui.auth.CodeVerifyScreen
import org.example.bubbleapp.ui.auth.ProfileSetupScreen
import org.example.bubbleapp.ui.chat.ChatScreen
import org.example.bubbleapp.ui.chat.ChatViewModel
import org.example.bubbleapp.ui.chat.ChatsListScreen
import kotlin.math.PI
import kotlin.math.atan2

// Навигация
sealed class Screen {
    // Auth
    object PhoneInput : Screen()
    object CodeVerify : Screen()
    object ProfileSetup : Screen()
    // Main
    object Home : Screen()
    object Bubbles : Screen()
    object Calls : Screen()
    object Chats : Screen()
    data class Chat(val chatId: String) : Screen()
}

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
    val scope = rememberCoroutineScope()
    val appState = remember { getAppState(scope) }
    val isInitialized by appState.isInitialized.collectAsState()
    val authState by appState.authService.authState.collectAsState()

    MaterialTheme {
        if (!isInitialized) {
            // Loading screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1a1a2e)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bubble",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            return@MaterialTheme
        }

        // Determine initial screen based on auth state
        var currentScreen by remember(authState) {
            mutableStateOf(
                when (authState) {
                    is AuthState.Authenticated -> Screen.Home
                    else -> Screen.PhoneInput
                }
            )
        }

        val authViewModel = appState.authViewModel

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            }
        ) { screen ->
            when (screen) {
                // Auth screens
                Screen.PhoneInput -> PhoneInputScreen(
                    viewModel = authViewModel,
                    onCodeSent = { currentScreen = Screen.CodeVerify }
                )

                Screen.CodeVerify -> CodeVerifyScreen(
                    viewModel = authViewModel,
                    onBack = { currentScreen = Screen.PhoneInput },
                    onVerified = { isNewUser ->
                        currentScreen = if (isNewUser) Screen.ProfileSetup else Screen.Home
                    }
                )

                Screen.ProfileSetup -> ProfileSetupScreen(
                    viewModel = authViewModel,
                    onComplete = { currentScreen = Screen.Home }
                )

                // Main screens
                Screen.Home -> HomeScreen(
                    onNavigateToBubbles = { currentScreen = Screen.Bubbles },
                    onNavigateToCalls = { currentScreen = Screen.Calls },
                    onNavigateToChats = { currentScreen = Screen.Chats },
                    onLogout = {
                        scope.launch {
                            appState.authService.logout()
                            currentScreen = Screen.PhoneInput
                        }
                    }
                )

                Screen.Bubbles -> BubblesScreen(
                    onBack = { currentScreen = Screen.Home }
                )

                Screen.Calls -> CallsScreen(
                    onBack = { currentScreen = Screen.Home }
                )

                Screen.Chats -> ChatsListScreen(
                    viewModel = appState.chatsViewModel,
                    onBack = { currentScreen = Screen.Home },
                    onChatClick = { chat ->
                        currentScreen = Screen.Chat(chat.id)
                    }
                )

                is Screen.Chat -> {
                    val currentUserId = appState.tokenManager.getUserId() ?: ""
                    val chatViewModel = remember(screen.chatId) {
                        ChatViewModel(
                            chatId = screen.chatId,
                            chatRepository = appState.chatRepository,
                            messageRepository = appState.messageRepository,
                            currentUserId = currentUserId,
                            scope = scope
                        )
                    }
                    ChatScreen(
                        viewModel = chatViewModel,
                        onBack = { currentScreen = Screen.Chats }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToBubbles: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToChats: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        // Logout button in top right
        Text(
            text = "Выйти",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable { onLogout() }
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Bubble",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Кнопка чатов
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .clickable { onNavigateToChats() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Чаты",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Сообщения и кружки",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка кружков
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .clickable { onNavigateToBubbles() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF6C63FF)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "O",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Кружки",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Записывай видео",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка звонков
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .clickable { onNavigateToCalls() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF00C9A7)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Звонки",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Аудио через интернет",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallsScreen(
    onBack: () -> Unit
) {
    val callManager = remember { CallManager() }
    val callState by callManager.callState.collectAsState()
    val currentCallerId by callManager.currentCallerId.collectAsState()
    val currentCallerName by callManager.currentCallerName.collectAsState()
    val isMuted by callManager.isMuted.collectAsState()
    val isSpeakerOn by callManager.isSpeakerOn.collectAsState()
    val errorMessage by callManager.errorMessage.collectAsState()
    val isConnected by callManager.isConnected.collectAsState()
    val connectionError by callManager.connectionError.collectAsState()

    var serverUrl by remember { mutableStateOf("wss://untribally-adverbless-rohan.ngrok-free.dev/call") }
    var userId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var myIP by remember { mutableStateOf("") }
    var targetUserId by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            callManager.disconnect()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Верхняя панель
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "<",
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Звонки",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            when {
                // Активный звонок
                callState == CallState.ACTIVE || callState == CallState.CONNECTING -> {
                    ActiveCallUI(
                        callerName = currentCallerName ?: currentCallerId ?: "Unknown",
                        callState = callState,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        onToggleMute = { callManager.toggleMute() },
                        onToggleSpeaker = { callManager.toggleSpeaker() },
                        onEndCall = { callManager.endCall() }
                    )
                }

                // Входящий звонок
                callState == CallState.RINGING -> {
                    IncomingCallUI(
                        callerName = currentCallerName ?: "Unknown",
                        onAccept = { callManager.acceptCall() },
                        onReject = { callManager.rejectCall() }
                    )
                }

                // Исходящий звонок (ожидание)
                callState == CallState.CALLING -> {
                    OutgoingCallUI(
                        targetUser = targetUserId,
                        onCancel = { callManager.endCall() }
                    )
                }

                // Ошибка звонка
                callState == CallState.FAILED -> {
                    ErrorUI(
                        errorMessage = errorMessage ?: "Неизвестная ошибка",
                        onDismiss = { callManager.clearError() }
                    )
                }

                // Не подключен к серверу
                !isConnected -> {
                    Column {
                        // Show connection error if any
                        connectionError?.let { error ->
                            Text(
                                text = error,
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x33FF0000))
                                    .padding(12.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        ConnectionUI(
                            serverUrl = serverUrl,
                            userId = userId,
                            userName = userName,
                            myIP = myIP,
                            onServerUrlChange = { serverUrl = it },
                            onUserIdChange = { userId = it },
                            onUserNameChange = { userName = it },
                            onMyIPChange = { myIP = it },
                            onConnect = {
                                if (userId.isNotBlank() && userName.isNotBlank()) {
                                    callManager.setLocalIP(myIP)
                                    callManager.connect(serverUrl, userId, userName)
                                }
                            }
                        )
                    }
                }

                // Подключен, можно звонить
                else -> {
                    DialUI(
                        userId = userId,
                        targetUserId = targetUserId,
                        onTargetUserIdChange = { targetUserId = it },
                        onCall = {
                            if (targetUserId.isNotBlank()) {
                                callManager.startCall(targetUserId)
                            }
                        },
                        onDisconnect = {
                            callManager.disconnect()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionUI(
    serverUrl: String,
    userId: String,
    userName: String,
    myIP: String,
    onServerUrlChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
    onMyIPChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Подключение к серверу",
            fontSize = 18.sp,
            color = Color.White
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Сервер") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00C9A7),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00C9A7),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        OutlinedTextField(
            value = userId,
            onValueChange = onUserIdChange,
            label = { Text("Ваш ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00C9A7),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00C9A7),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = { Text("Ваше имя") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00C9A7),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00C9A7),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        OutlinedTextField(
            value = myIP,
            onValueChange = onMyIPChange,
            label = { Text("Мой IP (WiFi)") },
            placeholder = { Text("192.168.0.xxx", color = Color.Gray.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00C9A7),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00C9A7),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00C9A7)
            )
        ) {
            Text("Подключиться", fontSize = 16.sp)
        }
    }
}

@Composable
private fun DialUI(
    userId: String,
    targetUserId: String,
    onTargetUserIdChange: (String) -> Unit,
    onCall: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Вы: $userId",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = targetUserId,
            onValueChange = onTargetUserIdChange,
            label = { Text("ID собеседника") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00C9A7),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00C9A7),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCall,
            modifier = Modifier
                .size(100.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text("C", fontSize = 32.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Gray
            )
        ) {
            Text("Отключиться")
        }
    }
}

@Composable
private fun IncomingCallUI(
    callerName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Входящий звонок",
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = callerName,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(64.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // Отклонить
            Button(
                onClick = onReject,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Text("X", fontSize = 24.sp)
            }

            // Принять
            Button(
                onClick = onAccept,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("V", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun OutgoingCallUI(
    targetUser: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Вызов...",
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = targetUser,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336)
            )
        ) {
            Text("X", fontSize = 24.sp)
        }
    }
}

@Composable
private fun ErrorUI(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Иконка ошибки
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFF44336), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Ошибка",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = errorMessage,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C63FF)
            )
        ) {
            Text("OK", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ActiveCallUI(
    callerName: String,
    callState: CallState,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    var callDuration by remember { mutableStateOf(0) }

    LaunchedEffect(callState) {
        if (callState == CallState.ACTIVE) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callDuration++
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Аватар
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFF6C63FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = callerName.firstOrNull()?.uppercase() ?: "?",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = callerName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (callState == CallState.CONNECTING) "Соединение..."
                   else formatDuration(callDuration),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(64.dp))

        // Кнопки управления
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Mute
            Button(
                onClick = onToggleMute,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMuted) Color(0xFFF44336) else Color.DarkGray
                )
            ) {
                Text(if (isMuted) "M" else "m", fontSize = 20.sp)
            }

            // Speaker
            Button(
                onClick = onToggleSpeaker,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSpeakerOn) Color(0xFF2196F3) else Color.DarkGray
                )
            ) {
                Text("S", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Завершить
        Button(
            onClick = onEndCall,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336)
            )
        ) {
            Text("X", fontSize = 24.sp)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

@Composable
fun BubblesScreen(
    onBack: () -> Unit
) {
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
            // Кнопка назад
            Text(
                text = "< Назад",
                fontSize = 18.sp,
                color = Color(0xFF6C63FF),
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(bottom = 16.dp)
            )

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
                        shape = RoundedCornerShape(8.dp)
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
