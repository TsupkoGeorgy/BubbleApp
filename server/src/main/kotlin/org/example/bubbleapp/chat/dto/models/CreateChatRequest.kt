package org.example.bubbleapp.chat.dto.models

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.example.bubbleapp.chat.entity.ChatType
import java.util.UUID

data class CreateChatRequest(
    val type: ChatType = ChatType.DIRECT,

    @field:Size(max = 100, message = "Chat name must be at most 100 characters")
    val name: String? = null,

    @field:NotEmpty(message = "At least one member is required")
    val memberIds: List<UUID>
)
