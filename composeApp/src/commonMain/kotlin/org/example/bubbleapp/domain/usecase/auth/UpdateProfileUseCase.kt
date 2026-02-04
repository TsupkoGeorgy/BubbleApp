package org.example.bubbleapp.domain.usecase.auth

import org.example.bubbleapp.data.auth.AuthStateHolder
import org.example.bubbleapp.data.datasource.remote.AuthRemoteDataSource
import org.example.bubbleapp.data.model.User

class UpdateProfileUseCase(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val authStateHolder: AuthStateHolder
) {
    suspend fun execute(username: String?, displayName: String?): Result<User> {
        return try {
            val user = authRemoteDataSource.updateProfile(username, displayName)
            authStateHolder.setCurrentUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
