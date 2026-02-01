package org.example.bubbleapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.ApiException
import org.example.bubbleapp.data.api.SendMessageRequest
import org.example.bubbleapp.data.model.Message

class MessageRepository(
    private val apiClient: ApiClient,
    private val attachmentRepository: AttachmentRepository? = null
) {
    // In-memory cache per chat
    private val messagesCache = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    fun getMessagesFlow(chatId: String): StateFlow<List<Message>> {
        return messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    }

    suspend fun loadMessages(
        chatId: String,
        before: String? = null,
        limit: Int = 50
    ): Result<List<Message>> {
        return try {
            val messages = apiClient.getMessages(chatId, before, limit)

            val flow = messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
            if (before == null) {
                // Initial load - replace all
                flow.value = messages
            } else {
                // Pagination - append older messages
                flow.value = flow.value + messages
            }

            Result.success(messages)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        chatId: String,
        content: String,
        replyToId: String? = null
    ): Result<Message> {
        return try {
            val message = apiClient.sendMessage(
                chatId,
                SendMessageRequest(
                    type = "TEXT",
                    content = content,
                    replyToId = replyToId
                )
            )

            // Add to cache
            val flow = messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
            flow.value = listOf(message) + flow.value

            Result.success(message)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            apiClient.deleteMessage(messageId)

            // Remove from cache
            messagesCache[chatId]?.let { flow ->
                flow.value = flow.value.filter { it.id != messageId }
            }

            Result.success(Unit)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsRead(chatId: String, messageId: String): Result<Unit> {
        return try {
            apiClient.markAsRead(chatId, messageId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Add message from WebSocket
    fun addMessage(chatId: String, message: Message) {
        val flow = messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        // Avoid duplicates
        if (flow.value.none { it.id == message.id }) {
            flow.value = listOf(message) + flow.value
        }
    }

    fun clearCache(chatId: String) {
        messagesCache.remove(chatId)
    }

    suspend fun sendVideoBubble(
        chatId: String,
        localFileName: String,
        localFilePath: String? = null,
        fileSize: Long = 0,
        durationMs: Int? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Message> {
        return try {
            val attachmentId: String?

            // If we have attachment repository and file info, upload to S3
            if (attachmentRepository != null && localFilePath != null && fileSize > 0) {
                val uploadResult = attachmentRepository.uploadVideo(
                    localFilePath = localFilePath,
                    fileName = localFileName,
                    fileSize = fileSize,
                    durationMs = durationMs,
                    onProgress = { progress ->
                        // Upload is 0-80% of total progress
                        onProgress(progress * 0.8f)
                    }
                )

                attachmentId = uploadResult.getOrNull()?.id
                if (attachmentId == null) {
                    return Result.failure(
                        uploadResult.exceptionOrNull() ?: Exception("Ошибка загрузки")
                    )
                }
            } else {
                // Fallback: no upload, just send filename
                attachmentId = null
            }

            onProgress(0.9f)

            // Send message with attachment
            val message = apiClient.sendMessage(
                chatId,
                SendMessageRequest(
                    type = "VIDEO_BUBBLE",
                    content = if (attachmentId == null) localFileName else null,
                    attachmentIds = if (attachmentId != null) listOf(attachmentId) else emptyList()
                )
            )

            onProgress(1.0f)

            // Add to cache
            val flow = messagesCache.getOrPut(chatId) { MutableStateFlow(emptyList()) }
            flow.value = listOf(message) + flow.value

            Result.success(message)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
