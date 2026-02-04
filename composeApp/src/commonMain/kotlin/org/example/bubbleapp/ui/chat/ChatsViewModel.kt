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
import org.example.bubbleapp.data.model.User
import org.example.bubbleapp.data.websocket.ChatWebSocketManager
import org.example.bubbleapp.data.websocket.NewChatEvent
import org.example.bubbleapp.data.websocket.NewMessageEvent
import org.example.bubbleapp.domain.usecase.chat.CreateDirectChatUseCase
import org.example.bubbleapp.domain.usecase.chat.GetChatsUseCase
import org.example.bubbleapp.domain.usecase.user.SearchUsersByPhoneUseCase

data class ChatsListState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false
)

data class NewChatDialogState(
    val isVisible: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

sealed class ChatsEvent {
    data class ChatCreated(val chat: Chat) : ChatsEvent()
}

class ChatsViewModel(
    private val getChatsUseCase: GetChatsUseCase,
    private val createDirectChatUseCase: CreateDirectChatUseCase,
    private val searchUsersByPhoneUseCase: SearchUsersByPhoneUseCase,
    private val scope: CoroutineScope,
    private val webSocketManager: ChatWebSocketManager? = null
) {
    private val _state = MutableStateFlow(ChatsListState())
    val state: StateFlow<ChatsListState> = _state

    private val _dialogState = MutableStateFlow(NewChatDialogState())
    val dialogState: StateFlow<NewChatDialogState> = _dialogState

    private val _events = MutableSharedFlow<ChatsEvent>()
    val events: SharedFlow<ChatsEvent> = _events.asSharedFlow()

    init {
        observeWebSocketEvents()
    }

    private fun observeWebSocketEvents() {
        webSocketManager?.let { ws ->
            scope.launch {
                ws.events.collect { event ->
                    when (event) {
                        is NewChatEvent -> {
                            loadChats()
                        }
                        is NewMessageEvent -> {
                            val currentChats = _state.value.chats
                            if (currentChats.none { it.id == event.chatId }) {
                                loadChats()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun loadChats() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            getChatsUseCase.execute().fold(
                onSuccess = { chats ->
                    _state.update { it.copy(chats = chats, isLoading = false) }
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

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }

        scope.launch {
            getChatsUseCase.execute().fold(
                onSuccess = { chats ->
                    _state.update { it.copy(chats = chats, isRefreshing = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = e.message
                        )
                    }
                }
            )
        }
    }

    fun showNewChatDialog() {
        _dialogState.value = NewChatDialogState(isVisible = true)
    }

    fun hideNewChatDialog() {
        _dialogState.value = NewChatDialogState(isVisible = false)
    }

    fun onSearchQueryChanged(query: String) {
        _dialogState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun searchUsers() {
        val query = _dialogState.value.searchQuery
        if (query.isBlank()) return

        _dialogState.update { it.copy(isSearching = true, errorMessage = null) }

        scope.launch {
            searchUsersByPhoneUseCase.execute(query).fold(
                onSuccess = { users ->
                    _dialogState.update {
                        it.copy(
                            searchResults = users,
                            isSearching = false
                        )
                    }
                },
                onFailure = { e ->
                    _dialogState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = e.message
                        )
                    }
                }
            )
        }
    }

    fun createChatWithUser(user: User) {
        _dialogState.update { it.copy(isCreating = true, errorMessage = null) }

        scope.launch {
            createDirectChatUseCase.execute(user.id).fold(
                onSuccess = { chat ->
                    _dialogState.value = NewChatDialogState(isVisible = false)
                    _events.emit(ChatsEvent.ChatCreated(chat))
                    loadChats()
                },
                onFailure = { e ->
                    _dialogState.update {
                        it.copy(
                            isCreating = false,
                            errorMessage = e.message
                        )
                    }
                }
            )
        }
    }
}
