package org.example.bubbleapp.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.bubbleapp.data.model.User

class AuthStateHolder(
    val tokenManager: TokenManager
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    val authState: StateFlow<AuthState> = tokenManager.authState

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
    }

    fun clearCurrentUser() {
        _currentUser.value = null
    }
}
