package org.example.bubbleapp.device.dto.models

import org.example.bubbleapp.device.entity.Platform
import java.time.Instant
import java.util.UUID

data class DeviceResponse(
    val id: UUID,
    val deviceId: String,
    val platform: Platform,
    val pushToken: String?,
    val appVersion: String?,
    val osVersion: String?,
    val deviceModel: String?,
    val lastActiveAt: Instant,
    val createdAt: Instant
)
