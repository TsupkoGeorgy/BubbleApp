package org.example.bubbleapp.device.mapper

import org.example.bubbleapp.device.dto.models.DeviceResponse
import org.example.bubbleapp.device.entity.Device

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
