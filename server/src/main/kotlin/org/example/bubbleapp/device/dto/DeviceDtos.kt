package org.example.bubbleapp.device.dto

import jakarta.validation.constraints.NotBlank
import org.example.bubbleapp.device.entity.Device
import org.example.bubbleapp.device.entity.Platform
import java.time.Instant
import java.util.UUID

data class RegisterDeviceRequest(
    @field:NotBlank(message = "Device ID is required")
    val deviceId: String,

    val platform: Platform,

    val pushToken: String? = null,

    val appVersion: String? = null,

    val osVersion: String? = null,

    val deviceModel: String? = null
)

data class UpdatePushTokenRequest(
    @field:NotBlank(message = "Push token is required")
    val pushToken: String
)

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

data class DeviceListResponse(
    val devices: List<DeviceResponse>,
    val total: Int
)

fun Device.toResponse() = DeviceResponse(
    id = id,
    deviceId = deviceId,
    platform = platform,
    pushToken = pushToken,
    appVersion = appVersion,
    osVersion = osVersion,
    deviceModel = deviceModel,
    lastActiveAt = lastActiveAt,
    createdAt = createdAt
)
