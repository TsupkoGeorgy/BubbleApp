package org.example.bubbleapp.device.dto.models

import jakarta.validation.constraints.NotBlank
import org.example.bubbleapp.device.entity.Platform

data class RegisterDeviceRequest(
    @field:NotBlank(message = "Device ID is required")
    val deviceId: String,

    val platform: Platform,

    val pushToken: String? = null,

    val appVersion: String? = null,

    val osVersion: String? = null,

    val deviceModel: String? = null
)
