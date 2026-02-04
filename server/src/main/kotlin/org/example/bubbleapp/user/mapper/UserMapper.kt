package org.example.bubbleapp.user.mapper

import org.example.bubbleapp.user.dto.models.UserResponse
import org.example.bubbleapp.user.entity.User

fun User.toResponse() = UserResponse(
    id = id,
    phone = phone,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    createdAt = createdAt
)
