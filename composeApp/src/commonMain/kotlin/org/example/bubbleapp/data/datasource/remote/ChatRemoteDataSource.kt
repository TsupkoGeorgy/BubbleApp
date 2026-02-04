package org.example.bubbleapp.data.datasource.remote

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.CreateChatRequest
import org.example.bubbleapp.data.model.Chat

class ChatRemoteDataSource(
    private val apiClient: ApiClient
) {
    suspend fun getChats(): List<Chat> {
        return apiClient.getChats()
    }

    suspend fun getChat(chatId: String): Chat {
        return apiClient.getChat(chatId)
    }

    suspend fun createDirectChat(otherUserId: String): Chat {
        return apiClient.createChat(
            CreateChatRequest(
                type = "DIRECT",
                memberIds = listOf(otherUserId)
            )
        )
    }

    suspend fun createGroupChat(name: String, memberIds: List<String>): Chat {
        return apiClient.createChat(
            CreateChatRequest(
                type = "GROUP",
                memberIds = memberIds,
                name = name
            )
        )
    }

    suspend fun deleteChat(chatId: String) {
        apiClient.deleteChat(chatId)
    }
}
