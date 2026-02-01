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
import org.example.bubbleapp.data.repository.ChatRepository
import org.example.bubbleapp.data.repository.MessageRepository
import org.example.bubbleapp.data.repository.UserRepository
import org.example.bubbleapp.ui.auth.AuthViewModel
import org.example.bubbleapp.ui.chat.ChatsViewModel

class AppState(
    private val scope: CoroutineScope,
    baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        // For iOS simulator use your Mac's IP or use ngrok
        // localhost doesn't work on iOS simulator
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8080"
    }

    // Data layer
    private val tokenStorage = TokenStorage()
    val tokenManager = TokenManager(tokenStorage)
    val apiClient = ApiClient(baseUrl, tokenManager)

    // Repositories
    val chatRepository = ChatRepository(apiClient)
    val userRepository = UserRepository(apiClient)
    val messageRepository = MessageRepository(apiClient)

    // Services
    val authService = AuthService(apiClient, tokenManager)

    // ViewModels
    val authViewModel = AuthViewModel(authService, scope)
    val chatsViewModel = ChatsViewModel(chatRepository, userRepository, scope)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    fun initialize() {
        scope.launch {
            authService.initialize()
            _isInitialized.value = true
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
