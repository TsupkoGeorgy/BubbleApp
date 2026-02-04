package org.example.bubbleapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.bubbleapp.data.datasource.remote.MessageRemoteDataSource
import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.data.websocket.ChatWebSocketManager

class MessageRepository(
    private val messageRemoteDataSource: MessageRemoteDataSource,
    private val webSocketManager: ChatWebSocketManager? = null
) {
    private val messagesCache = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    fun getMessagesFlow(chatId: String): StateFlow<List<Message>> {
        return messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    }

    suspend fun loadMessages(chatId: String, before: String? = null, limit: Int = 50): List<Message> {
        val messages = messageRemoteDataSource.getMessages(chatId, before, limit)

        val flow = messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        if (before == null) {
            flow.value = messages
        } else {
            flow.value = flow.value + messages
        }

        return messages
    }

    suspend fun sendMessage(chatId: String, content: String, replyToId: String? = null): Message {
        val message = messageRemoteDataSource.sendMessage(
            chatId = chatId,
            type = "TEXT",
            content = content,
            replyToId = replyToId
        )
        addMessage(chatId, message)
        return message
    }

    suspend fun sendVideoBubbleMessage(chatId: String, content: String?, attachmentIds: List<String>): Message {
        val message = messageRemoteDataSource.sendMessage(
            chatId = chatId,
            type = "VIDEO_BUBBLE",
            content = content,
            attachmentIds = attachmentIds
        )
        addMessage(chatId, message)
        return message
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        messageRemoteDataSource.deleteMessage(messageId)
        messagesCache[chatId]?.let { flow ->
            flow.value = flow.value.filter { it.id != messageId }
        }
    }

    suspend fun markAsRead(chatId: String, messageId: String) {
        messageRemoteDataSource.markAsRead(chatId, messageId)
    }

    fun addMessage(chatId: String, message: Message) {
        val flow = messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        if (flow.value.none { it.id == message.id }) {
            flow.value = listOf(message) + flow.value
        }
    }

    fun clearCache(chatId: String) {
        messagesCache.remove(chatId)
    }

    fun sendTypingIndicator(chatId: String) {
        webSocketManager?.sendTyping(chatId)
    }

    fun sendStopTyping(chatId: String) {
        webSocketManager?.sendStopTyping(chatId)
    }

    fun subscribeToChat(chatId: String) {
        webSocketManager?.subscribe(listOf(chatId))
    }

    fun unsubscribeFromChat(chatId: String) {
        webSocketManager?.unsubscribe(listOf(chatId))
    }
}
