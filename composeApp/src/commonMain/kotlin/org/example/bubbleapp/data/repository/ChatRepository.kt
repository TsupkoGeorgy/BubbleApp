package org.example.bubbleapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.ApiException
import org.example.bubbleapp.data.api.CreateChatRequest
import org.example.bubbleapp.data.model.Chat

class ChatRepository(
    private val apiClient: ApiClient
) {
    // In-memory cache (later can be replaced with SQLDelight)
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    suspend fun refreshChats(): Result<List<Chat>> {
        return try {
            val chats = apiClient.getChats()
            _chats.value = chats
            Result.success(chats)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChat(chatId: String): Result<Chat> {
        return try {
            val chat = apiClient.getChat(chatId)
            Result.success(chat)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectChat(otherUserId: String): Result<Chat> {
        return try {
            val chat = apiClient.createChat(
                CreateChatRequest(
                    type = "DIRECT",
                    memberIds = listOf(otherUserId)
                )
            )
            // Update cache
            _chats.value = listOf(chat) + _chats.value
            Result.success(chat)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(name: String, memberIds: List<String>): Result<Chat> {
        return try {
            val chat = apiClient.createChat(
                CreateChatRequest(
                    type = "GROUP",
                    memberIds = memberIds,
                    name = name
                )
            )
            _chats.value = listOf(chat) + _chats.value
            Result.success(chat)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            apiClient.deleteChat(chatId)
            _chats.value = _chats.value.filter { it.id != chatId }
            Result.success(Unit)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
