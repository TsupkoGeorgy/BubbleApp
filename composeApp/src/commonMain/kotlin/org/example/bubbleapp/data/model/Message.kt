package org.example.bubbleapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MessageType {
    TEXT,
    VIDEO_BUBBLE,
    VOICE,
    STICKER
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Serializable
data class ReplyTo(
    val id: String,
    val senderId: String,
    val senderName: String? = null,
    val type: String,
    val content: String? = null
)

@Serializable
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String? = null, // может отсутствовать, если есть sender
    val type: String, // "TEXT", "VIDEO_BUBBLE", "VOICE", "STICKER"
    val content: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val replyTo: ReplyTo? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val status: String = "SENT", // local status
    val sender: User? = null
) {
    /** ID отправителя: из senderId или sender.id */
    val effectiveSenderId: String
        get() = senderId ?: sender?.id ?: ""
}

@Serializable
data class Attachment(
    val id: String,
    val type: String, // "VIDEO", "IMAGE", "VOICE", "FILE"
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null
)
