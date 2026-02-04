package org.example.bubbleapp.domain.usecase.auth

import org.example.bubbleapp.data.auth.AuthStateHolder
import org.example.bubbleapp.data.auth.AuthTokens
import org.example.bubbleapp.data.auth.currentTimeMillis
import org.example.bubbleapp.data.datasource.remote.AuthRemoteDataSource
import org.example.bubbleapp.data.model.User

data class VerifyCodeResult(
    val user: User,
    val isNewUser: Boolean
)

class VerifyCodeUseCase(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val authStateHolder: AuthStateHolder
) {
    suspend fun execute(phone: String, code: String): Result<VerifyCodeResult> {
        return try {
            val response = authRemoteDataSource.verifyCode(phone, code)

            val tokens = AuthTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAt = currentTimeMillis() + response.expiresIn * 1000,
                userId = response.user.id
            )

            authStateHolder.tokenManager.saveTokens(tokens)

            val user = authRemoteDataSource.getMe()
            authStateHolder.setCurrentUser(user)

            Result.success(VerifyCodeResult(user, response.user.isNewUser))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
