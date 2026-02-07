package org.example.bubbleapp.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.bubbleapp.data.auth.currentTimeMillis
import org.example.bubbleapp.data.model.Chat
import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.data.repository.MessageRepository
import org.example.bubbleapp.data.websocket.ChatWebSocketManager
import org.example.bubbleapp.data.websocket.ChatWsEvent
import org.example.bubbleapp.data.websocket.MessageDeletedEvent
import org.example.bubbleapp.data.websocket.MessageEditedEvent
import org.example.bubbleapp.data.websocket.MessageReadEvent
import org.example.bubbleapp.data.websocket.NewChatEvent
import org.example.bubbleapp.data.websocket.NewMessageEvent
import org.example.bubbleapp.data.websocket.StopTypingEvent
import org.example.bubbleapp.data.websocket.TypingEvent
import org.example.bubbleapp.data.websocket.UserOfflineEvent
import org.example.bubbleapp.data.websocket.UserOnlineEvent
import org.example.bubbleapp.data.websocket.WsErrorEvent
import org.example.bubbleapp.domain.usecase.chat.GetChatUseCase
import org.example.bubbleapp.domain.usecase.message.DeleteMessageUseCase
import org.example.bubbleapp.domain.usecase.message.LoadMessagesUseCase
import org.example.bubbleapp.domain.usecase.message.MarkAsReadUseCase
import org.example.bubbleapp.domain.usecase.message.SendMessageUseCase
import org.example.bubbleapp.domain.usecase.message.SendVideoBubbleUseCase

data class ChatState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val errorMessage: String? = null,
    val messageText: String = "",
    val isRecording: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val isTyping: Boolean = false,
    val typingUserName: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: String? = null
)

sealed class ChatEvent {
    data class Error(val message: String) : ChatEvent()
    data object MessageSent : ChatEvent()
    data object VideoBubbleSent : ChatEvent()
}

class ChatViewModel(
    private val chatId: String,
    private val currentUserId: String,
    private val getChatUseCase: GetChatUseCase,
    private val loadMessagesUseCase: LoadMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendVideoBubbleUseCase: SendVideoBubbleUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val messageRepository: MessageRepository,
    private val scope: CoroutineScope,
    private val webSocketManager: ChatWebSocketManager? = null
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var typingJob: Job? = null

    init {
        loadChat()
        loadMessages()
        observeMessages()
        subscribeToWebSocket()
    }

    private fun subscribeToWebSocket() {
        messageRepository.subscribeToChat(chatId)

        webSocketManager?.let { ws ->
            scope.launch {
                ws.events.collect { event ->
                    handleWebSocketEvent(event)
                }
            }
        }
    }

    private fun handleWebSocketEvent(event: ChatWsEvent) {
        when (event) {
            is NewMessageEvent -> {
                if (event.chatId == chatId) {
                    messageRepository.addMessage(chatId, event.message)
                }
            }
            is TypingEvent -> {
                if (event.chatId == chatId && event.userId != currentUserId) {
                    onTypingReceived(event.username)
                }
            }
            is StopTypingEvent -> {
                if (event.chatId == chatId && event.userId != currentUserId) {
                    _state.update { it.copy(isTyping = false, typingUserName = null) }
                }
            }
            is UserOnlineEvent -> {
                val chat = _state.value.chat
                if (chat?.type == "PRIVATE") {
                    val partner = chat.members.find { it.userId != currentUserId }
                    if (partner?.userId == event.userId) {
                        onUserOnlineChanged(true, null)
                    }
                }
            }
            is UserOfflineEvent -> {
                val chat = _state.value.chat
                if (chat?.type == "PRIVATE") {
                    val partner = chat.members.find { it.userId != currentUserId }
                    if (partner?.userId == event.userId) {
                        onUserOnlineChanged(false, null)
                    }
                }
            }
            is MessageDeletedEvent -> {
                if (event.chatId == chatId) {
                    messageRepository.clearCache(chatId)
                    loadMessages()
                }
            }
            is MessageEditedEvent -> {
                if (event.chatId == chatId) {
                    loadMessages()
                }
            }
            is MessageReadEvent -> {}
            is WsErrorEvent -> {
                scope.launch {
                    _events.emit(ChatEvent.Error(event.message))
                }
            }
            is NewChatEvent -> {}
        }
    }

    fun onCleared() {
        messageRepository.unsubscribeFromChat(chatId)
    }

    private fun observeMessages() {
        scope.launch {
            messageRepository.getMessagesFlow(chatId).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    private fun loadChat() {
        scope.launch {
            getChatUseCase.execute(chatId).fold(
                onSuccess = { chat ->
                    _state.update { it.copy(chat = chat) }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message) }
                }
            )
        }
    }

    fun loadMessages() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            loadMessagesUseCase.execute(chatId).fold(
                onSuccess = { messages ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasMoreMessages = messages.size >= 50
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Ошибка загрузки"
                        )
                    }
                }
            )
        }
    }

    fun loadMoreMessages() {
        val currentState = _state.value
        if (currentState.isLoadingMore || !currentState.hasMoreMessages) return

        val oldestMessage = currentState.messages.lastOrNull() ?: return

        _state.update { it.copy(isLoadingMore = true) }

        scope.launch {
            loadMessagesUseCase.execute(chatId, before = oldestMessage.id).fold(
                onSuccess = { messages ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMoreMessages = messages.size >= 50
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoadingMore = false) }
                    _events.emit(ChatEvent.Error(e.message ?: "Ошибка загрузки"))
                }
            )
        }
    }

    fun onMessageTextChanged(text: String) {
        _state.update { it.copy(messageText = text) }
        if (text.isNotEmpty()) {
            sendTypingIndicator()
        }
    }

    private var lastTypingSentTime = 0L

    private fun sendTypingIndicator() {
        val now = currentTimeMillis()
        if (now - lastTypingSentTime > 3000) {
            lastTypingSentTime = now
            messageRepository.sendTypingIndicator(chatId)
        }
    }

    fun onTypingReceived(userName: String?) {
        _state.update { it.copy(isTyping = true, typingUserName = userName) }
        typingJob?.cancel()
        typingJob = scope.launch {
            delay(4000)
            _state.update { it.copy(isTyping = false, typingUserName = null) }
        }
    }

    fun onUserOnlineChanged(isOnline: Boolean, lastSeen: String?) {
        _state.update { it.copy(isOnline = isOnline, lastSeen = lastSeen) }
    }

    fun sendMessage() {
        val text = _state.value.messageText.trim()
        if (text.isBlank()) return

        _state.update { it.copy(isSending = true, messageText = "") }

        scope.launch {
            sendMessageUseCase.execute(chatId, text).fold(
                onSuccess = {
                    _state.update { it.copy(isSending = false) }
                    _events.emit(ChatEvent.MessageSent)
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            messageText = text
                        )
                    }
                    _events.emit(ChatEvent.Error(e.message ?: "Ошибка отправки"))
                }
            )
        }
    }

    fun deleteMessage(messageId: String) {
        scope.launch {
            deleteMessageUseCase.execute(chatId, messageId).onFailure { e ->
                _events.emit(ChatEvent.Error(e.message ?: "Ошибка удаления"))
            }
        }
    }

    fun isOwnMessage(message: Message): Boolean {
        return message.effectiveSenderId == currentUserId
    }

    fun getPartnerUserId(): String? {
        val chat = _state.value.chat ?: return null
        if (chat.type != "PRIVATE") return null
        return chat.members.find { it.userId != currentUserId }?.userId
    }

    fun markAsRead() {
        val lastMessage = _state.value.messages.firstOrNull() ?: return
        scope.launch {
            markAsReadUseCase.execute(chatId, lastMessage.id)
        }
    }

    fun startRecording() {
        _state.update { it.copy(isRecording = true) }
    }

    fun cancelRecording() {
        _state.update { it.copy(isRecording = false) }
    }

    fun onVideoRecorded(fileName: String, filePath: String, fileSize: Long) {
        _state.update { it.copy(isRecording = false, isUploading = true, uploadProgress = 0f) }

        scope.launch {
            sendVideoBubbleUseCase.execute(
                chatId = chatId,
                localFileName = fileName,
                localFilePath = filePath,
                fileSize = fileSize,
                durationMs = null,
                onProgress = { progress ->
                    _state.update { it.copy(uploadProgress = progress) }
                }
            ).fold(
                onSuccess = {
                    _state.update { it.copy(isUploading = false) }
                    _events.emit(ChatEvent.VideoBubbleSent)
                },
                onFailure = { e ->
                    _state.update { it.copy(isUploading = false) }
                    _events.emit(ChatEvent.Error(e.message ?: "Ошибка отправки видео"))
                }
            )
        }
    }
}
