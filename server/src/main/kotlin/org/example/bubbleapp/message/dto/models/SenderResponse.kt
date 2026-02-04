package org.example.bubbleapp.message.dto.models

import java.util.UUID

data class SenderResponse(
    val id: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?
)
