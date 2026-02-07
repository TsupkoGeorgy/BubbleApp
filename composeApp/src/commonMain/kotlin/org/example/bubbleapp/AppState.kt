package org.example.bubbleapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.auth.AuthState
import org.example.bubbleapp.data.auth.AuthStateHolder
import org.example.bubbleapp.data.auth.TokenManager
import org.example.bubbleapp.data.auth.TokenStorage
import org.example.bubbleapp.data.repository.MessageRepository
import org.example.bubbleapp.data.websocket.ChatWebSocketManager
import org.example.bubbleapp.di.dataSourceModule
import org.example.bubbleapp.di.repositoryModule
import org.example.bubbleapp.di.useCaseModule
import org.example.bubbleapp.domain.usecase.auth.InitializeAuthUseCase
import org.example.bubbleapp.domain.usecase.auth.LogoutUseCase
import org.example.bubbleapp.domain.usecase.chat.CreateDirectChatUseCase
import org.example.bubbleapp.domain.usecase.chat.GetChatUseCase
import org.example.bubbleapp.domain.usecase.message.DeleteMessageUseCase
import org.example.bubbleapp.domain.usecase.message.LoadMessagesUseCase
import org.example.bubbleapp.domain.usecase.message.MarkAsReadUseCase
import org.example.bubbleapp.domain.usecase.message.SendMessageUseCase
import org.example.bubbleapp.domain.usecase.message.SendVideoBubbleUseCase
import org.example.bubbleapp.domain.usecase.user.GetMyProfileUseCase
import org.example.bubbleapp.domain.usecase.user.GetUserProfileUseCase
import org.example.bubbleapp.domain.usecase.user.UpdateMyProfileUseCase
import org.example.bubbleapp.domain.usecase.user.UploadAvatarUseCase
import org.example.bubbleapp.ui.auth.AuthViewModel
import org.example.bubbleapp.ui.chat.ChatViewModel
import org.example.bubbleapp.ui.chat.ChatsViewModel
import org.example.bubbleapp.ui.profile.MyProfileViewModel
import org.example.bubbleapp.ui.profile.UserProfileViewModel

class AppState(
    private val scope: CoroutineScope,
    baseUrl: String = DEFAULT_BASE_URL
) : DIAware {

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.0.143:8080"
        val DEFAULT_WS_URL: String
            get() = DEFAULT_BASE_URL.replace("http://", "ws://") + "/ws/chat"
    }

    private val coroutineScope: CoroutineScope = scope

    override val di: DI = DI {
        // Core
        bindSingleton { TokenStorage() }
        bindSingleton { TokenManager(instance()) }
        bindSingleton { AuthStateHolder(instance()) }
        bindSingleton { ApiClient(baseUrl, instance()) }

        // WebSocket
        bindSingleton {
            ChatWebSocketManager(
                wsUrl = baseUrl.replace("http://", "ws://") + "/ws/chat",
                tokenManager = instance(),
                scope = coroutineScope
            )
        }

        // Import other modules
        import(dataSourceModule)
        import(repositoryModule)
        import(useCaseModule)

        // ViewModels
        bindSingleton {
            AuthViewModel(
                sendCodeUseCase = instance(),
                verifyCodeUseCase = instance(),
                updateProfileUseCase = instance(),
                scope = coroutineScope
            )
        }

        bindSingleton {
            ChatsViewModel(
                getChatsUseCase = instance(),
                createDirectChatUseCase = instance(),
                searchUsersByPhoneUseCase = instance(),
                scope = coroutineScope,
                webSocketManager = instanceOrNull()
            )
        }
    }

    // Expose commonly used instances
    val tokenManager: TokenManager by instance()
    val authStateHolder: AuthStateHolder by instance()
    val chatWebSocketManager: ChatWebSocketManager by instance()
    val messageRepository: MessageRepository by instance()

    // ViewModels
    val authViewModel: AuthViewModel by instance()
    val chatsViewModel: ChatsViewModel by instance()

    // UseCases for creating ChatViewModel
    private val getChatUseCase: GetChatUseCase by instance()
    private val loadMessagesUseCase: LoadMessagesUseCase by instance()
    private val sendMessageUseCase: SendMessageUseCase by instance()
    private val sendVideoBubbleUseCase: SendVideoBubbleUseCase by instance()
    private val deleteMessageUseCase: DeleteMessageUseCase by instance()
    private val markAsReadUseCase: MarkAsReadUseCase by instance()
    private val initializeAuthUseCase: InitializeAuthUseCase by instance()
    private val logoutUseCase: LogoutUseCase by instance()

    // UseCases for profile ViewModels
    private val getMyProfileUseCase: GetMyProfileUseCase by instance()
    private val updateMyProfileUseCase: UpdateMyProfileUseCase by instance()
    private val uploadAvatarUseCase: UploadAvatarUseCase by instance()
    private val getUserProfileUseCase: GetUserProfileUseCase by instance()
    private val createDirectChatUseCase: CreateDirectChatUseCase by instance()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    val authState: StateFlow<AuthState>
        get() = authStateHolder.authState

    fun initialize() {
        coroutineScope.launch {
            initializeAuthUseCase.execute()
            _isInitialized.value = true

            if (authStateHolder.authState.value is AuthState.Authenticated) {
                chatWebSocketManager.connect()
            }
        }

        coroutineScope.launch {
            authStateHolder.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> chatWebSocketManager.connect()
                    else -> chatWebSocketManager.disconnect()
                }
            }
        }
    }

    suspend fun logout() {
        logoutUseCase.execute()
    }

    fun createChatViewModel(chatId: String): ChatViewModel {
        val currentUserId = tokenManager.getUserId() ?: ""
        return ChatViewModel(
            chatId = chatId,
            currentUserId = currentUserId,
            getChatUseCase = getChatUseCase,
            loadMessagesUseCase = loadMessagesUseCase,
            sendMessageUseCase = sendMessageUseCase,
            sendVideoBubbleUseCase = sendVideoBubbleUseCase,
            deleteMessageUseCase = deleteMessageUseCase,
            markAsReadUseCase = markAsReadUseCase,
            messageRepository = messageRepository,
            scope = coroutineScope,
            webSocketManager = chatWebSocketManager
        )
    }

    fun createMyProfileViewModel(): MyProfileViewModel {
        return MyProfileViewModel(
            getMyProfileUseCase = getMyProfileUseCase,
            updateMyProfileUseCase = updateMyProfileUseCase,
            uploadAvatarUseCase = uploadAvatarUseCase,
            logoutUseCase = logoutUseCase,
            authStateHolder = authStateHolder,
            scope = coroutineScope
        )
    }

    fun createUserProfileViewModel(userId: String): UserProfileViewModel {
        return UserProfileViewModel(
            userId = userId,
            getUserProfileUseCase = getUserProfileUseCase,
            createDirectChatUseCase = createDirectChatUseCase,
            scope = coroutineScope
        )
    }
}

private var appStateInstance: AppState? = null

fun getAppState(scope: CoroutineScope): AppState {
    return appStateInstance ?: AppState(scope).also {
        appStateInstance = it
        it.initialize()
    }
}
