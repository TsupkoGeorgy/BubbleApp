package org.example.bubbleapp.domain.usecase.message

import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.data.repository.MessageRepository

class SendMessageUseCase(
    private val messageRepository: MessageRepository
) {
    suspend fun execute(chatId: String, content: String, replyToId: String? = null): Result<Message> {
        return try {
            val message = messageRepository.sendMessage(chatId, content, replyToId)
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
