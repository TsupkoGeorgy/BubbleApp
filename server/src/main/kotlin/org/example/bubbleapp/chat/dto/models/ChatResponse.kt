package org.example.bubbleapp.chat.dto.models

import org.example.bubbleapp.chat.entity.ChatType
import java.time.Instant
import java.util.UUID

data class ChatResponse(
    val id: UUID,
    val type: ChatType,
    val name: String?,
    val avatarUrl: String?,
    val members: List<ChatMemberResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)
