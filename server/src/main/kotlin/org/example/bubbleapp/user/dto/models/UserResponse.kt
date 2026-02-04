package org.example.bubbleapp.user.dto.models

import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: Instant
)
