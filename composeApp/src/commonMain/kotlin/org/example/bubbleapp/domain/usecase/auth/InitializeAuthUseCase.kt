package org.example.bubbleapp.domain.usecase.auth

import org.example.bubbleapp.data.auth.AuthState
import org.example.bubbleapp.data.auth.AuthStateHolder
import org.example.bubbleapp.data.auth.AuthTokens
import org.example.bubbleapp.data.auth.currentTimeMillis
import org.example.bubbleapp.data.datasource.remote.AuthRemoteDataSource
import org.example.bubbleapp.data.model.User

class InitializeAuthUseCase(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val authStateHolder: AuthStateHolder
) {
    suspend fun execute(): Result<User?> {
        return try {
            authStateHolder.tokenManager.initialize()

            if (authStateHolder.authState.value is AuthState.Authenticated) {
                try {
                    val user = authRemoteDataSource.getMe()
                    authStateHolder.setCurrentUser(user)
                    Result.success(user)
                } catch (e: Exception) {
                    val refreshResult = tryRefreshToken()
                    if (refreshResult.isSuccess) {
                        Result.success(refreshResult.getOrNull())
                    } else {
                        Result.success(null)
                    }
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun tryRefreshToken(): Result<User> {
        val refreshToken = authStateHolder.tokenManager.getRefreshToken()
            ?: return Result.failure(Exception("No refresh token"))

        return try {
            val response = authRemoteDataSource.refreshToken(refreshToken)

            val tokens = AuthTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAt = currentTimeMillis() + response.expiresIn * 1000,
                userId = response.user.id
            )

            authStateHolder.tokenManager.saveTokens(tokens)
            val user = authRemoteDataSource.getMe()
            authStateHolder.setCurrentUser(user)
            Result.success(user)
        } catch (e: Exception) {
            authStateHolder.tokenManager.logout()
            Result.failure(e)
        }
    }
}
