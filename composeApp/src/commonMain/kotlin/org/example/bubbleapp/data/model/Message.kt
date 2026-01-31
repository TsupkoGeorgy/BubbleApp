package org.example.bubbleapp.data.model

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
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val type: String, // "TEXT", "VIDEO_BUBBLE", "VOICE", "STICKER"
    val content: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val replyToId: String? = null,
    val replyTo: Message? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val status: String = "SENT", // local status
    val sender: User? = null
)

@Serializable
data class Attachment(
    val id: String,
    val type: String, // "VIDEO", "IMAGE", "VOICE", "FILE"
    val url: String,
    val thumbnailUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val duration: Int? = null, // seconds for video/voice
    val width: Int? = null,
    val height: Int? = null
)
