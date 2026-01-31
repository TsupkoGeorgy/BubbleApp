package org.example.bubbleapp.device.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.example.bubbleapp.device.dto.*
import org.example.bubbleapp.device.service.DeviceService
import org.example.bubbleapp.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/devices")
@Tag(name = "Devices", description = "Device registration for push notifications")
class DeviceController(
    private val deviceService: DeviceService
) {

    @PostMapping
    @Operation(summary = "Register device for push notifications")
    fun registerDevice(
        @AuthenticationPrincipal user: UserPrincipal,
        @Valid @RequestBody request: RegisterDeviceRequest
    ): ResponseEntity<DeviceResponse> {
        val response = deviceService.registerDevice(user.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    @Operation(summary = "Get all registered devices")
    fun getDevices(
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<DeviceListResponse> {
        val response = deviceService.getUserDevices(user.userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{deviceId}/push-token")
    @Operation(summary = "Update push token for device")
    fun updatePushToken(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable deviceId: String,
        @Valid @RequestBody request: UpdatePushTokenRequest
    ): ResponseEntity<DeviceResponse> {
        val response = deviceService.updatePushToken(user.userId, deviceId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Unregister device")
    fun removeDevice(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable deviceId: String
    ): ResponseEntity<Void> {
        deviceService.removeDevice(user.userId, deviceId)
        return ResponseEntity.noContent().build()
    }
}
