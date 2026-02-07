package org.example.bubbleapp.domain.usecase.user

import org.example.bubbleapp.data.model.User
import org.example.bubbleapp.data.repository.UserRepository

class UploadAvatarUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(imageData: ByteArray, fileName: String): Result<User> {
        return try {
            val user = userRepository.uploadAvatar(imageData, fileName)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
