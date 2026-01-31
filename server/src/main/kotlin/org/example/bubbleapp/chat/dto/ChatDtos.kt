package org.example.bubbleapp.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.example.bubbleapp.chat.entity.Chat
import org.example.bubbleapp.chat.entity.ChatMember
import org.example.bubbleapp.chat.entity.ChatType
import org.example.bubbleapp.chat.entity.MemberRole
import java.time.Instant
import java.util.UUID

data class CreateChatRequest(
    val type: ChatType = ChatType.DIRECT,

    @field:Size(max = 100, message = "Chat name must be at most 100 characters")
    val name: String? = null,

    @field:NotEmpty(message = "At least one member is required")
    val memberIds: List<UUID>
)

data class ChatResponse(
    val id: UUID,
    val type: ChatType,
    val name: String?,
    val avatarUrl: String?,
    val members: List<ChatMemberResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ChatListResponse(
    val chats: List<ChatResponse>,
    val total: Int
)

data class ChatMemberResponse(
    val userId: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val role: MemberRole,
    val joinedAt: Instant
)

data class AddMemberRequest(
    @field:NotEmpty(message = "User ID is required")
    val userId: UUID
)

data class UpdateChatRequest(
    @field:Size(max = 100, message = "Chat name must be at most 100 characters")
    val name: String? = null
)

fun Chat.toResponse(): ChatResponse = ChatResponse(
    id = id,
    type = type,
    name = name,
    avatarUrl = avatarUrl,
    members = members.map { it.toResponse() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatMember.toResponse(): ChatMemberResponse = ChatMemberResponse(
    userId = user.id,
    phone = user.phone,
    username = user.username,
    displayName = user.displayName,
    avatarUrl = user.avatarUrl,
    role = role,
    joinedAt = joinedAt
)
