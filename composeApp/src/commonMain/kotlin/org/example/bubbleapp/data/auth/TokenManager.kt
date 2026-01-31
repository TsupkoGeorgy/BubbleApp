package org.example.bubbleapp.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String
)

expect class TokenStorage() {
    suspend fun saveTokens(tokens: AuthTokens)
    suspend fun getTokens(): AuthTokens?
    suspend fun clearTokens()
}

class TokenManager(private val storage: TokenStorage) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    private var currentTokens: AuthTokens? = null

    suspend fun initialize() {
        currentTokens = storage.getTokens()
        _authState.value = if (currentTokens != null) {
            AuthState.Authenticated(currentTokens!!.userId)
        } else {
            AuthState.NotAuthenticated
        }
    }

    suspend fun saveTokens(tokens: AuthTokens) {
        currentTokens = tokens
        storage.saveTokens(tokens)
        _authState.value = AuthState.Authenticated(tokens.userId)
    }

    fun getAccessToken(): String? = currentTokens?.accessToken

    fun getRefreshToken(): String? = currentTokens?.refreshToken

    fun getUserId(): String? = currentTokens?.userId

    fun isTokenExpired(): Boolean {
        val tokens = currentTokens ?: return true
        return currentTimeMillis() > tokens.expiresAt - 60_000 // 1 min buffer
    }

    suspend fun logout() {
        currentTokens = null
        storage.clearTokens()
        _authState.value = AuthState.NotAuthenticated
    }
}

sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val userId: String) : AuthState()
}

// Multiplatform System.currentTimeMillis()
expect fun currentTimeMillis(): Long
