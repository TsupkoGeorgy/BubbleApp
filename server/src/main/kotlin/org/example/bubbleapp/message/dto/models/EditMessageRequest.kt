package org.example.bubbleapp.message.dto.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EditMessageRequest(
    @field:NotBlank(message = "Content is required")
    @field:Size(max = 10000, message = "Message content is too long")
    val content: String
)
