package org.example.bubbleapp.domain.usecase.user

import org.example.bubbleapp.data.model.User
import org.example.bubbleapp.data.repository.UserRepository

class UpdateMyProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(displayName: String?, username: String?): Result<User> {
        return try {
            val user = userRepository.updateMyProfile(displayName, username)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
