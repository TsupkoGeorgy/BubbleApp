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
import org.example.bubbleapp.data.repository.ChatRepository
import org.example.bubbleapp.data.repository.UserRepository

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
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatsListState())
    val state: StateFlow<ChatsListState> = _state

    private val _dialogState = MutableStateFlow(NewChatDialogState())
    val dialogState: StateFlow<NewChatDialogState> = _dialogState

    private val _events = MutableSharedFlow<ChatsEvent>()
    val events: SharedFlow<ChatsEvent> = _events.asSharedFlow()

    init {
        loadChats()
    }

    // ===== Chats List =====

    fun loadChats() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            chatRepository.refreshChats().fold(
                onSuccess = { chats ->
                    _state.update { it.copy(chats = chats, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Ошибка загрузки"
                    )}
                }
            )
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }

        scope.launch {
            chatRepository.refreshChats().fold(
                onSuccess = { chats ->
                    _state.update { it.copy(chats = chats, isRefreshing = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(
                        isRefreshing = false,
                        errorMessage = e.message
                    )}
                }
            )
        }
    }


    // ===== New Chat Dialog =====

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
            userRepository.searchByPhone(query).fold(
                onSuccess = { users ->
                    _dialogState.update { it.copy(
                        searchResults = users,
                        isSearching = false
                    )}
                },
                onFailure = { e ->
                    _dialogState.update { it.copy(
                        isSearching = false,
                        errorMessage = e.message
                    )}
                }
            )
        }
    }

    fun createChatWithUser(user: User) {
        _dialogState.update { it.copy(isCreating = true, errorMessage = null) }

        scope.launch {
            chatRepository.createDirectChat(user.id).fold(
                onSuccess = { chat ->
                    _dialogState.value = NewChatDialogState(isVisible = false)
                    _events.emit(ChatsEvent.ChatCreated(chat))
                    loadChats()
                },
                onFailure = { e ->
                    _dialogState.update { it.copy(
                        isCreating = false,
                        errorMessage = e.message
                    )}
                }
            )
        }
    }
}
