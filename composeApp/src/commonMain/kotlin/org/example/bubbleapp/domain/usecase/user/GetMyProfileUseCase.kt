package org.example.bubbleapp.domain.usecase.user

import org.example.bubbleapp.data.model.User
import org.example.bubbleapp.data.repository.UserRepository

class GetMyProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(): Result<User> {
        return try {
            val user = userRepository.getMyProfile()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
