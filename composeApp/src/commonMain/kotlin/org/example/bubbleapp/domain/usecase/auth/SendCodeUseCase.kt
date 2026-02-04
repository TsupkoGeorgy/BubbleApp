package org.example.bubbleapp.domain.usecase.auth

import org.example.bubbleapp.data.datasource.remote.AuthRemoteDataSource

class SendCodeUseCase(
    private val authRemoteDataSource: AuthRemoteDataSource
) {
    suspend fun execute(phone: String): Result<Unit> {
        return try {
            authRemoteDataSource.sendCode(phone)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
