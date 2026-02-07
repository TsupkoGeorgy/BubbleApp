package org.example.bubbleapp.domain.usecase.user

import org.example.bubbleapp.data.model.User
import org.example.bubbleapp.data.repository.UserRepository

class GetUserProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(userId: String): Result<User> {
        return try {
            val user = userRepository.getUser(userId)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
