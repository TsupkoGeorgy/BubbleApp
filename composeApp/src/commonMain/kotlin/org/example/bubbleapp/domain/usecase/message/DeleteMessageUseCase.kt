package org.example.bubbleapp.domain.usecase.message

import org.example.bubbleapp.data.repository.MessageRepository

class DeleteMessageUseCase(
    private val messageRepository: MessageRepository
) {
    suspend fun execute(chatId: String, messageId: String): Result<Unit> {
        return try {
            messageRepository.deleteMessage(chatId, messageId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
