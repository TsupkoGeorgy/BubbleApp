package org.example.bubbleapp.ui.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.bubbleapp.domain.usecase.auth.SendCodeUseCase
import org.example.bubbleapp.domain.usecase.auth.UpdateProfileUseCase
import org.example.bubbleapp.domain.usecase.auth.VerifyCodeUseCase

data class PhoneInputState(
    val phone: String = "+7",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class CodeVerifyState(
    val phone: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ProfileSetupState(
    val displayName: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class AuthEvent {
    data class CodeSent(val phone: String) : AuthEvent()
    data class Verified(val isNewUser: Boolean) : AuthEvent()
    data object ProfileSaved : AuthEvent()
}

class AuthViewModel(
    private val sendCodeUseCase: SendCodeUseCase,
    private val verifyCodeUseCase: VerifyCodeUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val scope: CoroutineScope
) {
    private val _phoneState = MutableStateFlow(PhoneInputState())
    val phoneState: StateFlow<PhoneInputState> = _phoneState

    private val _codeState = MutableStateFlow(CodeVerifyState())
    val codeState: StateFlow<CodeVerifyState> = _codeState

    private val _profileState = MutableStateFlow(ProfileSetupState())
    val profileState: StateFlow<ProfileSetupState> = _profileState

    private val _events = MutableStateFlow<AuthEvent?>(null)
    val events: StateFlow<AuthEvent?> = _events

    fun clearEvent() {
        _events.value = null
    }

    fun onPhoneChanged(phone: String) {
        val filtered = phone.filter { it.isDigit() || (it == '+' && phone.indexOf(it) == 0) }
        _phoneState.update { it.copy(phone = filtered, errorMessage = null) }
    }

    fun sendCode() {
        val phone = _phoneState.value.phone
        if (phone.length < 10) {
            _phoneState.update { it.copy(errorMessage = "Введите корректный номер телефона") }
            return
        }

        _phoneState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            sendCodeUseCase.execute(phone).fold(
                onSuccess = {
                    _codeState.update { it.copy(phone = phone) }
                    _events.value = AuthEvent.CodeSent(phone)
                    _phoneState.update { it.copy(isLoading = false) }
                },
                onFailure = { e ->
                    _phoneState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Ошибка отправки кода"
                        )
                    }
                }
            )
        }
    }

    fun onCodeChanged(code: String) {
        val filtered = code.filter { it.isDigit() }.take(6)
        _codeState.update { it.copy(code = filtered, errorMessage = null) }

        if (filtered.length == 4 && !_codeState.value.isLoading) {
            verifyCode()
        }
    }

    fun verifyCode() {
        val state = _codeState.value
        if (state.code.length < 4 || state.isLoading) return

        _codeState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            verifyCodeUseCase.execute(state.phone, state.code).fold(
                onSuccess = { result ->
                    _events.value = AuthEvent.Verified(result.isNewUser)
                },
                onFailure = { e ->
                    _codeState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка верификации")
                    }
                }
            )
        }
    }

    fun onDisplayNameChanged(name: String) {
        _profileState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun onUsernameChanged(username: String) {
        val filtered = username.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        _profileState.update { it.copy(username = filtered, errorMessage = null) }
    }

    fun saveProfile() {
        val state = _profileState.value
        if (state.displayName.isBlank()) {
            _profileState.update { it.copy(errorMessage = "Введите ваше имя") }
            return
        }

        _profileState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            updateProfileUseCase.execute(
                username = state.username.takeIf { it.isNotBlank() },
                displayName = state.displayName.takeIf { it.isNotBlank() }
            ).fold(
                onSuccess = {
                    _events.value = AuthEvent.ProfileSaved
                },
                onFailure = { e ->
                    _profileState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка сохранения")
                    }
                }
            )
        }
    }

    fun skipProfile() {
        _events.value = AuthEvent.ProfileSaved
    }

    fun resetToPhoneInput() {
        _phoneState.value = PhoneInputState()
        _codeState.value = CodeVerifyState()
    }
}
