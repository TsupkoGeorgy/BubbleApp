package org.example.bubbleapp.domain.usecase.chat

import org.example.bubbleapp.data.model.Chat
import org.example.bubbleapp.data.repository.ChatRepository

class GetChatUseCase(
    private val chatRepository: ChatRepository
) {
    suspend fun execute(chatId: String): Result<Chat> {
        return try {
            val chat = chatRepository.getChat(chatId)
            Result.success(chat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
