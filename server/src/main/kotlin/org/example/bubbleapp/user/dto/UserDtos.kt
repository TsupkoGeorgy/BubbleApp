package org.example.bubbleapp.user.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.example.bubbleapp.user.entity.User
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

data class UpdateProfileRequest(
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers and underscores")
    val username: String? = null,

    @field:Size(max = 100, message = "Display name must be at most 100 characters")
    val displayName: String? = null
)

data class UserSearchResponse(
    val users: List<UserResponse>,
    val total: Int
)

fun User.toResponse() = UserResponse(
    id = id,
    phone = phone,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    createdAt = createdAt
)
