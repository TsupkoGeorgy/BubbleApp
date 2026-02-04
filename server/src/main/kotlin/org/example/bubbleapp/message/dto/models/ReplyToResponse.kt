package org.example.bubbleapp.message.dto.models

import org.example.bubbleapp.message.entity.MessageType
import java.util.UUID

data class ReplyToResponse(
    val id: UUID,
    val senderId: UUID,
    val senderName: String?,
    val type: MessageType,
    val content: String?
)
