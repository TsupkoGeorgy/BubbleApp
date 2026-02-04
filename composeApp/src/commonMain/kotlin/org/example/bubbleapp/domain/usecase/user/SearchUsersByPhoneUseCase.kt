package org.example.bubbleapp.domain.usecase.user

import org.example.bubbleapp.data.model.User
import org.example.bubbleapp.data.repository.UserRepository

class SearchUsersByPhoneUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(phone: String): Result<List<User>> {
        return try {
            val users = userRepository.searchByPhone(phone)
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
