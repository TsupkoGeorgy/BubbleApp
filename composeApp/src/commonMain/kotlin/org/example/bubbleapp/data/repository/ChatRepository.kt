package org.example.bubbleapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.bubbleapp.data.datasource.remote.ChatRemoteDataSource
import org.example.bubbleapp.data.model.Chat

class ChatRepository(
    private val chatRemoteDataSource: ChatRemoteDataSource
) {
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    suspend fun getChats(): List<Chat> {
        val chats = chatRemoteDataSource.getChats()
        _chats.value = chats
        return chats
    }

    suspend fun getChat(chatId: String): Chat {
        return chatRemoteDataSource.getChat(chatId)
    }

    suspend fun createDirectChat(otherUserId: String): Chat {
        val chat = chatRemoteDataSource.createDirectChat(otherUserId)
        _chats.value = listOf(chat) + _chats.value
        return chat
    }

    suspend fun createGroupChat(name: String, memberIds: List<String>): Chat {
        val chat = chatRemoteDataSource.createGroupChat(name, memberIds)
        _chats.value = listOf(chat) + _chats.value
        return chat
    }

    suspend fun deleteChat(chatId: String) {
        chatRemoteDataSource.deleteChat(chatId)
        _chats.value = _chats.value.filter { it.id != chatId }
    }
}
