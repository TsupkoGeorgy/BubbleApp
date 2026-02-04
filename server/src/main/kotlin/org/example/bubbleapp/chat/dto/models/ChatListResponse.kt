package org.example.bubbleapp.chat.dto.models

data class ChatListResponse(
    val chats: List<ChatResponse>,
    val total: Int
)
