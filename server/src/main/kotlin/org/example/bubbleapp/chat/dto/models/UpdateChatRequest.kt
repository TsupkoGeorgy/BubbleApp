package org.example.bubbleapp.chat.dto.models

import jakarta.validation.constraints.Size

data class UpdateChatRequest(
    @field:Size(max = 100, message = "Chat name must be at most 100 characters")
    val name: String? = null
)
