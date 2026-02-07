package org.example.bubbleapp.ui.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.bubbleapp.data.auth.AuthStateHolder
import org.example.bubbleapp.domain.usecase.auth.LogoutUseCase
import org.example.bubbleapp.domain.usecase.user.GetMyProfileUseCase
import org.example.bubbleapp.domain.usecase.user.UpdateMyProfileUseCase
import org.example.bubbleapp.domain.usecase.user.UploadAvatarUseCase

class MyProfileViewModel(
    private val getMyProfileUseCase: GetMyProfileUseCase,
    private val updateMyProfileUseCase: UpdateMyProfileUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val authStateHolder: AuthStateHolder,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(MyProfileViewState())
    val state: StateFlow<MyProfileViewState> = _state

    private val _events = MutableSharedFlow<MyProfileViewEvent>()
    val events: SharedFlow<MyProfileViewEvent> = _events.asSharedFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            getMyProfileUseCase.execute().fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            user = user,
                            isLoading = false,
                            editedDisplayName = user.displayName ?: "",
                            editedUsername = user.username ?: "",
                            hasChanges = false
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

    fun onDisplayNameChanged(name: String) {
        if (name.length > 64) return

        val error = validateDisplayName(name)
        _state.update {
            it.copy(
                editedDisplayName = name,
                hasChanges = hasChanges(name, it.editedUsername),
                validationErrors = it.validationErrors.copy(displayNameError = error)
            )
        }
    }

    fun onUsernameChanged(username: String) {
        val filtered = username.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        if (filtered.length > 32) return

        val error = validateUsername(filtered)
        _state.update {
            it.copy(
                editedUsername = filtered,
                hasChanges = hasChanges(it.editedDisplayName, filtered),
                validationErrors = it.validationErrors.copy(usernameError = error)
            )
        }
    }

    private fun hasChanges(displayName: String, username: String): Boolean {
        val user = _state.value.user ?: return false
        return displayName != (user.displayName ?: "") || username != (user.username ?: "")
    }

    private fun validateDisplayName(name: String): String? {
        return when {
            name.isBlank() -> "Имя не может быть пустым"
            name.length < 1 -> "Минимум 1 символ"
            else -> null
        }
    }

    private fun validateUsername(username: String): String? {
        if (username.isEmpty()) return null // username is optional
        return when {
            username.length < 3 -> "Минимум 3 символа"
            !username.matches(Regex("^[a-z0-9_]+$")) -> "Только латиница, цифры и _"
            else -> null
        }
    }

    fun saveProfile() {
        val currentState = _state.value

        // Validate
        val displayNameError = validateDisplayName(currentState.editedDisplayName)
        val usernameError = validateUsername(currentState.editedUsername)

        if (displayNameError != null || usernameError != null) {
            _state.update {
                it.copy(
                    validationErrors = ValidationErrors(
                        displayNameError = displayNameError,
                        usernameError = usernameError
                    )
                )
            }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        scope.launch {
            updateMyProfileUseCase.execute(
                displayName = currentState.editedDisplayName.takeIf { it.isNotBlank() },
                username = currentState.editedUsername.takeIf { it.isNotBlank() }
            ).fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            user = user,
                            isSaving = false,
                            hasChanges = false
                        )
                    }
                    _events.emit(MyProfileViewEvent.ShowToast("Сохранено"))
                },
                onFailure = { e ->
                    val errorMsg = when {
                        e.message?.contains("409") == true ||
                        e.message?.contains("already taken") == true -> "Этот username уже занят"
                        else -> e.message ?: "Ошибка сохранения"
                    }
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = errorMsg,
                            validationErrors = if (errorMsg.contains("username")) {
                                it.validationErrors.copy(usernameError = errorMsg)
                            } else {
                                it.validationErrors
                            }
                        )
                    }
                }
            )
        }
    }

    fun showImagePicker() {
        _state.update { it.copy(showImagePicker = true) }
    }

    fun hideImagePicker() {
        _state.update { it.copy(showImagePicker = false) }
    }

    fun uploadAvatar(imageData: ByteArray, fileName: String) {
        _state.update { it.copy(isUploadingAvatar = true, showImagePicker = false, errorMessage = null) }

        scope.launch {
            uploadAvatarUseCase.execute(imageData, fileName).fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            user = user,
                            isUploadingAvatar = false
                        )
                    }
                    _events.emit(MyProfileViewEvent.ShowToast("Аватар обновлен"))
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isUploadingAvatar = false,
                            errorMessage = e.message ?: "Ошибка загрузки аватара"
                        )
                    }
                }
            )
        }
    }

    fun showLogoutConfirmation() {
        _state.update { it.copy(showLogoutConfirmation = true) }
    }

    fun hideLogoutConfirmation() {
        _state.update { it.copy(showLogoutConfirmation = false) }
    }

    fun logout() {
        _state.update { it.copy(showLogoutConfirmation = false) }

        scope.launch {
            logoutUseCase.execute()
            _events.emit(MyProfileViewEvent.LoggedOut)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
