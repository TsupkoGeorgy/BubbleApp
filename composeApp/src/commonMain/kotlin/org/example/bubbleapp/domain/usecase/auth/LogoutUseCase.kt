package org.example.bubbleapp.domain.usecase.auth

import org.example.bubbleapp.data.auth.AuthStateHolder
import org.example.bubbleapp.data.datasource.remote.AuthRemoteDataSource

class LogoutUseCase(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val authStateHolder: AuthStateHolder
) {
    suspend fun execute(): Result<Unit> {
        return try {
            try {
                authRemoteDataSource.logout()
            } catch (e: Exception) {
                // Ignore logout errors
            }
            authStateHolder.clearCurrentUser()
            authStateHolder.tokenManager.logout()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
