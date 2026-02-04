package org.example.bubbleapp.chat.dto.models

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class AddMemberRequest(
    @field:NotEmpty(message = "User ID is required")
    val userId: UUID
)
