package org.example.bubbleapp.ui.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.bubbleapp.domain.usecase.chat.CreateDirectChatUseCase
import org.example.bubbleapp.domain.usecase.user.GetUserProfileUseCase

class UserProfileViewModel(
    private val userId: String,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val createDirectChatUseCase: CreateDirectChatUseCase,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(UserProfileViewState())
    val state: StateFlow<UserProfileViewState> = _state

    private val _events = MutableSharedFlow<UserProfileViewEvent>()
    val events: SharedFlow<UserProfileViewEvent> = _events.asSharedFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            getUserProfileUseCase.execute(userId).fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            user = user,
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Ошибка загрузки профиля"
                        )
                    }
                }
            )
        }
    }

    fun onWriteClick() {
        val existingChatId = _state.value.existingChatId
        if (existingChatId != null) {
            scope.launch {
                _events.emit(UserProfileViewEvent.NavigateToChat(existingChatId))
            }
            return
        }

        // Create new chat
        scope.launch {
            createDirectChatUseCase.execute(userId).fold(
                onSuccess = { chat ->
                    _state.update { it.copy(existingChatId = chat.id) }
                    _events.emit(UserProfileViewEvent.NavigateToChat(chat.id))
                },
                onFailure = { e ->
                    _events.emit(UserProfileViewEvent.ShowError(e.message ?: "Ошибка создания чата"))
                }
            )
        }
    }

    fun onCallClick() {
        scope.launch {
            _events.emit(UserProfileViewEvent.NavigateToCall(userId))
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
