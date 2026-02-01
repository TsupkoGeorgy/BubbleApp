package org.example.bubbleapp.data.api

import kotlinx.serialization.Serializable

// Auth
@Serializable
data class SendCodeRequest(val phone: String)

@Serializable
data class SendCodeResponse(
    val success: Boolean = true,
    val message: String
)

@Serializable
data class VerifyCodeRequest(
    val phone: String,
    val code: String
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: AuthUser
)

@Serializable
data class AuthUser(
    val id: String,
    val phone: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null
) {
    val isNewUser: Boolean
        get() = displayName == null && username == null
}

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

// User
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null
)

// Chat
@Serializable
data class CreateChatRequest(
    val type: String = "DIRECT", // DIRECT or GROUP
    val memberIds: List<String>,
    val name: String? = null
)

// Message
@Serializable
data class SendMessageRequest(
    val type: String = "TEXT",
    val content: String? = null,
    val attachmentIds: List<String> = emptyList(),
    val replyToId: String? = null
)

@Serializable
data class EditMessageRequest(val content: String)

// Attachment
@Serializable
data class UploadUrlRequest(
    val type: String, // "VIDEO", "IMAGE", "VOICE", "FILE"
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null
)

@Serializable
data class UploadUrlResponse(
    val uploadUrl: String,
    val key: String,
    val expiresIn: Int = 900
)

@Serializable
data class ConfirmUploadRequest(
    val key: String,
    val type: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null
)

// Error
@Serializable
data class ApiError(
    val error: String,
    val code: String? = null,
    val details: Map<String, String>? = null
)

// Pagination
@Serializable
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val size: Int,
    val number: Int
)

// Chats response
@Serializable
data class ChatsResponse(
    val chats: List<org.example.bubbleapp.data.model.Chat>,
    val total: Int
)

// Users search response
@Serializable
data class UsersSearchResponse(
    val users: List<org.example.bubbleapp.data.model.User>,
    val total: Int
)

// Messages response
@Serializable
data class MessagesResponse(
    val messages: List<org.example.bubbleapp.data.model.Message>,
    val total: Long,
    val hasMore: Boolean
)
