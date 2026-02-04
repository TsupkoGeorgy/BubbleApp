package org.example.bubbleapp.message.mapper

import org.example.bubbleapp.message.dto.models.AttachmentResponse
import org.example.bubbleapp.message.dto.models.MessageResponse
import org.example.bubbleapp.message.dto.models.ReplyToResponse
import org.example.bubbleapp.message.dto.models.SenderResponse
import org.example.bubbleapp.message.entity.Message

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
