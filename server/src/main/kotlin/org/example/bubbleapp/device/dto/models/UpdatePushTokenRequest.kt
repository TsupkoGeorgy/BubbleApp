package org.example.bubbleapp.device.dto.models

import jakarta.validation.constraints.NotBlank

data class UpdatePushTokenRequest(
    @field:NotBlank(message = "Push token is required")
    val pushToken: String
)
