package org.example.bubbleapp.domain.usecase.message

import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.data.repository.MessageRepository

class LoadMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    suspend fun execute(chatId: String, before: String? = null, limit: Int = 50): Result<List<Message>> {
        return try {
            val messages = messageRepository.loadMessages(chatId, before, limit)
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
