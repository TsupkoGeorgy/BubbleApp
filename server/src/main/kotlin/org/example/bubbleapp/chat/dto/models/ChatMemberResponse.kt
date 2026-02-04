package org.example.bubbleapp.chat.dto.models

import org.example.bubbleapp.chat.entity.MemberRole
import java.time.Instant
import java.util.UUID

data class ChatMemberResponse(
    val userId: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val role: MemberRole,
    val joinedAt: Instant
)
