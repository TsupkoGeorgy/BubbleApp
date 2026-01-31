package org.example.bubbleapp.message.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.example.bubbleapp.message.entity.Message
import org.example.bubbleapp.message.entity.MessageType
import java.time.Instant
import java.util.UUID

data class SendMessageRequest(
    val type: MessageType = MessageType.TEXT,

    @field:Size(max = 10000, message = "Message content is too long")
    val content: String? = null,

    val replyToId: UUID? = null,

    // For attachments (will be used in Phase 5)
    val attachmentIds: List<UUID>? = null
)

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

data class SenderResponse(
    val id: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?
)

data class ReplyToResponse(
    val id: UUID,
    val senderId: UUID,
    val senderName: String?,
    val type: MessageType,
    val content: String?
)

data class AttachmentResponse(
    val id: UUID,
    val type: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val url: String?,
    val thumbnailUrl: String?,
    val durationMs: Int?,
    val width: Int?,
    val height: Int?
)

data class MessageListResponse(
    val messages: List<MessageResponse>,
    val total: Long,
    val hasMore: Boolean
)

data class EditMessageRequest(
    @field:NotBlank(message = "Content is required")
    @field:Size(max = 10000, message = "Message content is too long")
    val content: String
)

fun Message.toResponse(): MessageResponse = MessageResponse(
    id = id,
    chatId = chat.id,
    sender = SenderResponse(
        id = sender.id,
        phone = sender.phone,
        username = sender.username,
        displayName = sender.displayName,
        avatarUrl = sender.avatarUrl
    ),
    type = type,
    content = content,
    replyTo = replyTo?.let { reply ->
        ReplyToResponse(
            id = reply.id,
            senderId = reply.sender.id,
            senderName = reply.sender.displayName ?: reply.sender.username ?: reply.sender.phone,
            type = reply.type,
            content = reply.content?.take(100) // Preview only
        )
    },
    attachments = attachments.map { att ->
        AttachmentResponse(
            id = att.id,
            type = att.type.name,
            fileName = att.fileName,
            fileSize = att.fileSize,
            mimeType = att.mimeType,
            url = null, // Will be filled by service with presigned URL
            thumbnailUrl = null,
            durationMs = att.durationMs,
            width = att.width,
            height = att.height
        )
    },
    createdAt = createdAt,
    updatedAt = updatedAt
)
