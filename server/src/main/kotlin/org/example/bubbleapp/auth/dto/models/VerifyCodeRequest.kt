package org.example.bubbleapp.auth.dto.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyCodeRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Invalid phone number format")
    val phone: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Pattern(regexp = "^\\d{4}$", message = "Code must be 4 digits")
    val code: String,

    val deviceId: String? = null
)
