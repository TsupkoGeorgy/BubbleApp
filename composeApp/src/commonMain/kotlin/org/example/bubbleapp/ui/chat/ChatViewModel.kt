package org.example.bubbleapp.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.bubbleapp.data.model.Chat
import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.data.repository.ChatRepository
import org.example.bubbleapp.data.repository.MessageRepository

data class ChatState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val errorMessage: String? = null,
    val messageText: String = ""
)

sealed class ChatEvent {
    data class Error(val message: String) : ChatEvent()
    object MessageSent : ChatEvent()
}

class ChatViewModel(
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val currentUserId: String,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    init {
        loadChat()
        loadMessages()
        observeMessages()
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
            chatRepository.getChat(chatId).fold(
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
            messageRepository.loadMessages(chatId).fold(
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
            messageRepository.loadMessages(chatId, before = oldestMessage.id).fold(
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
    }

    fun sendMessage() {
        val text = _state.value.messageText.trim()
        if (text.isBlank()) return

        _state.update { it.copy(isSending = true, messageText = "") }

        scope.launch {
            messageRepository.sendMessage(chatId, text).fold(
                onSuccess = {
                    _state.update { it.copy(isSending = false) }
                    _events.emit(ChatEvent.MessageSent)
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            messageText = text // Restore text on failure
                        )
                    }
                    _events.emit(ChatEvent.Error(e.message ?: "Ошибка отправки"))
                }
            )
        }
    }

    fun deleteMessage(messageId: String) {
        scope.launch {
            messageRepository.deleteMessage(chatId, messageId).onFailure { e ->
                _events.emit(ChatEvent.Error(e.message ?: "Ошибка удаления"))
            }
        }
    }

    fun isOwnMessage(message: Message): Boolean {
        return message.senderId == currentUserId
    }

    fun markAsRead() {
        val lastMessage = _state.value.messages.firstOrNull() ?: return
        scope.launch {
            messageRepository.markAsRead(chatId, lastMessage.id)
        }
    }
}
