package org.example.bubbleapp.auth.dto.models

import java.util.UUID

data class UserDto(
    val id: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?
)
