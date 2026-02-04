package org.example.bubbleapp.data.datasource.remote

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.SendMessageRequest
import org.example.bubbleapp.data.model.Message

class MessageRemoteDataSource(
    private val apiClient: ApiClient
) {
    suspend fun getMessages(chatId: String, before: String? = null, limit: Int = 50): List<Message> {
        return apiClient.getMessages(chatId, before, limit)
    }

    suspend fun sendMessage(chatId: String, type: String, content: String?, replyToId: String? = null, attachmentIds: List<String> = emptyList()): Message {
        return apiClient.sendMessage(
            chatId,
            SendMessageRequest(
                type = type,
                content = content,
                replyToId = replyToId,
                attachmentIds = attachmentIds
            )
        )
    }

    suspend fun editMessage(messageId: String, content: String): Message {
        return apiClient.editMessage(messageId, content)
    }

    suspend fun deleteMessage(messageId: String) {
        apiClient.deleteMessage(messageId)
    }

    suspend fun markAsRead(chatId: String, messageId: String) {
        apiClient.markAsRead(chatId, messageId)
    }

    suspend fun sendTypingIndicator(chatId: String) {
        apiClient.sendTypingIndicator(chatId)
    }
}
