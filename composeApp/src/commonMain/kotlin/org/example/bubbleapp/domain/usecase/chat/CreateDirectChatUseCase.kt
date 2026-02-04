package org.example.bubbleapp.domain.usecase.chat

import org.example.bubbleapp.data.model.Chat
import org.example.bubbleapp.data.repository.ChatRepository

class CreateDirectChatUseCase(
    private val chatRepository: ChatRepository
) {
    suspend fun execute(userId: String): Result<Chat> {
        return try {
            val chat = chatRepository.createDirectChat(userId)
            Result.success(chat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
