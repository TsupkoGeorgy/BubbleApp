package org.example.bubbleapp.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.ApiException
import org.example.bubbleapp.data.model.User

sealed class AuthResult {
    data class Success(val isNewUser: Boolean) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthService(
    private val apiClient: ApiClient,
    private val tokenManager: TokenManager
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    val authState: StateFlow<AuthState> = tokenManager.authState

    suspend fun initialize() {
        tokenManager.initialize()
        if (tokenManager.authState.value is AuthState.Authenticated) {
            try {
                _currentUser.value = apiClient.getMe()
            } catch (e: Exception) {
                // Token might be expired, try to refresh
                tryRefreshToken()
            }
        }
    }

    suspend fun sendCode(phone: String): Result<Unit> {
        return try {
            apiClient.sendCode(phone)
            Result.success(Unit)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyCode(phone: String, code: String): AuthResult {
        return try {
            val response = apiClient.verifyCode(phone, code)

            val tokens = AuthTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAt = currentTimeMillis() + response.expiresIn * 1000, // convert seconds to ms
                userId = response.user.id
            )

            tokenManager.saveTokens(tokens)

            // Fetch user profile
            _currentUser.value = apiClient.getMe()

            AuthResult.Success(response.user.isNewUser)
        } catch (e: ApiException) {
            AuthResult.Error(e.error.error)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun tryRefreshToken(): Boolean {
        val refreshToken = tokenManager.getRefreshToken() ?: return false

        return try {
            val response = apiClient.refreshToken(refreshToken)

            val tokens = AuthTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAt = currentTimeMillis() + response.expiresIn * 1000,
                userId = response.user.id
            )

            tokenManager.saveTokens(tokens)
            _currentUser.value = apiClient.getMe()
            true
        } catch (e: Exception) {
            tokenManager.logout()
            false
        }
    }

    suspend fun logout() {
        try {
            apiClient.logout()
        } catch (e: Exception) {
            // Ignore logout errors
        }
        _currentUser.value = null
        tokenManager.logout()
    }

    suspend fun updateProfile(username: String?, displayName: String?): Result<User> {
        return try {
            val user = apiClient.updateProfile(
                org.example.bubbleapp.data.api.UpdateProfileRequest(
                    username = username,
                    displayName = displayName
                )
            )
            _currentUser.value = user
            Result.success(user)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
