package org.example.bubbleapp.auth.dto.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class SendCodeRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Invalid phone number format")
    val phone: String
)
