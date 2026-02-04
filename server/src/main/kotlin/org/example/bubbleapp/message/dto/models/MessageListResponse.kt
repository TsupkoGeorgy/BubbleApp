package org.example.bubbleapp.message.dto.models

data class MessageListResponse(
    val messages: List<MessageResponse>,
    val total: Long,
    val hasMore: Boolean
)
