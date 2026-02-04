package org.example.bubbleapp.message.dto.models

import org.example.bubbleapp.message.entity.MessageType
import java.time.Instant
import java.util.UUID

data class MessageResponse(
    val id: UUID,
    val chatId: UUID,
    val sender: SenderResponse,
    val type: MessageType,
    val content: String?,
    val replyTo: ReplyToResponse?,
    val attachments: List<AttachmentResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)
