package org.example.bubbleapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.auth.AuthService
import org.example.bubbleapp.data.auth.AuthState
import org.example.bubbleapp.data.auth.TokenManager
import org.example.bubbleapp.data.auth.TokenStorage
import org.example.bubbleapp.data.repository.AttachmentRepository
import org.example.bubbleapp.data.repository.ChatRepository
import org.example.bubbleapp.data.repository.MessageRepository
import org.example.bubbleapp.data.repository.UserRepository
import org.example.bubbleapp.data.websocket.ChatWebSocketManager
import org.example.bubbleapp.ui.auth.AuthViewModel
import org.example.bubbleapp.ui.chat.ChatsViewModel

class AppState(
    private val scope: CoroutineScope,
    baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        // For iOS simulator use your Mac's IP or use ngrok
        // Use your Mac's local IP for real device testing
        // localhost/127.0.0.1 doesn't work on real devices
        const val DEFAULT_BASE_URL = "http://192.168.0.199:8080"
        val DEFAULT_WS_URL: String
            get() = DEFAULT_BASE_URL.replace("http://", "ws://") + "/ws/chat"
    }

    // Data layer
    private val tokenStorage = TokenStorage()
    val tokenManager = TokenManager(tokenStorage)
    val apiClient = ApiClient(baseUrl, tokenManager)

    // WebSocket
    val chatWebSocketManager = ChatWebSocketManager(
        wsUrl = baseUrl.replace("http://", "ws://") + "/ws/chat",
        tokenManager = tokenManager,
        scope = scope
    )

    // Repositories
    val chatRepository = ChatRepository(apiClient)
    val userRepository = UserRepository(apiClient)
    val attachmentRepository = AttachmentRepository(apiClient)
    val messageRepository = MessageRepository(apiClient, attachmentRepository, chatWebSocketManager)

    // Services
    val authService = AuthService(apiClient, tokenManager)

    // ViewModels
    val authViewModel = AuthViewModel(authService, scope)
    val chatsViewModel = ChatsViewModel(chatRepository, userRepository, scope, chatWebSocketManager)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    fun initialize() {
        scope.launch {
            authService.initialize()
            _isInitialized.value = true

            // Connect WebSocket if authenticated
            if (authService.authState.value is AuthState.Authenticated) {
                chatWebSocketManager.connect()
            }
        }

        // Observe auth state and connect/disconnect WebSocket
        scope.launch {
            authService.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> chatWebSocketManager.connect()
                    else -> chatWebSocketManager.disconnect()
                }
            }
        }
    }
}

// Singleton for now - will replace with DI later
private var appStateInstance: AppState? = null

fun getAppState(scope: CoroutineScope): AppState {
    return appStateInstance ?: AppState(scope).also {
        appStateInstance = it
        it.initialize()
    }
}
