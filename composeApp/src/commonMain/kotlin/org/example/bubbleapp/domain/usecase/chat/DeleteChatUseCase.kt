package org.example.bubbleapp.domain.usecase.chat

import org.example.bubbleapp.data.repository.ChatRepository

class DeleteChatUseCase(
    private val chatRepository: ChatRepository
) {
    suspend fun execute(chatId: String): Result<Unit> {
        return try {
            chatRepository.deleteChat(chatId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
