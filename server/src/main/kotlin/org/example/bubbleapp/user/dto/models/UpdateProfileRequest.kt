package org.example.bubbleapp.user.dto.models

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers and underscores")
    val username: String? = null,

    @field:Size(max = 100, message = "Display name must be at most 100 characters")
    val displayName: String? = null
)
