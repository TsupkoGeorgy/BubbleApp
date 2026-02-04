package org.example.bubbleapp.domain.usecase.chat

import org.example.bubbleapp.data.model.Chat
import org.example.bubbleapp.data.repository.ChatRepository

class GetChatsUseCase(
    private val chatRepository: ChatRepository
) {
    suspend fun execute(): Result<List<Chat>> {
        return try {
            val chats = chatRepository.getChats()
            Result.success(chats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
