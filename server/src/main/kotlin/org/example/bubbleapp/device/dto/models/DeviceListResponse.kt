package org.example.bubbleapp.device.dto.models

data class DeviceListResponse(
    val devices: List<DeviceResponse>,
    val total: Int
)
