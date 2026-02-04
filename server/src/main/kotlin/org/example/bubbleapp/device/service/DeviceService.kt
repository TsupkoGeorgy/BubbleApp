package org.example.bubbleapp.device.service

import org.example.bubbleapp.common.exception.EntityNotFoundException
import org.example.bubbleapp.device.dto.models.*
import org.example.bubbleapp.device.mapper.toResponse
import org.example.bubbleapp.device.entity.Device
import org.example.bubbleapp.device.repository.DeviceRepository
import org.example.bubbleapp.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(DeviceService::class.java)

    @Transactional
    fun registerDevice(userId: UUID, request: RegisterDeviceRequest): DeviceResponse {
        val user = userRepository.findById(userId).orElseThrow {
            EntityNotFoundException("User not found: $userId")
        }

        // Check if device already exists
        val existingDevice = deviceRepository.findByUserIdAndDeviceId(userId, request.deviceId)

        val device = if (existingDevice != null) {
            // Update existing device
            existingDevice.apply {
                pushToken = request.pushToken
                appVersion = request.appVersion
                osVersion = request.osVersion
                deviceModel = request.deviceModel
                lastActiveAt = Instant.now()
            }
        } else {
            // Create new device
            Device(
                user = user,
                deviceId = request.deviceId,
                platform = request.platform,
                pushToken = request.pushToken,
                appVersion = request.appVersion,
                osVersion = request.osVersion,
                deviceModel = request.deviceModel
            )
        }

        val saved = deviceRepository.save(device)
        log.info("Device ${saved.deviceId} registered for user $userId")

        return saved.toResponse()
    }

    @Transactional
    fun updatePushToken(userId: UUID, deviceId: String, request: UpdatePushTokenRequest): DeviceResponse {
        val device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw EntityNotFoundException("Device not found: $deviceId")

        device.pushToken = request.pushToken
        device.lastActiveAt = Instant.now()

        val saved = deviceRepository.save(device)
        log.info("Push token updated for device $deviceId")

        return saved.toResponse()
    }

    fun getUserDevices(userId: UUID): DeviceListResponse {
        val devices = deviceRepository.findAllByUserId(userId)
        return DeviceListResponse(
            devices = devices.map { it.toResponse() },
            total = devices.size
        )
    }

    @Transactional
    fun removeDevice(userId: UUID, deviceId: String) {
        deviceRepository.deleteByUserIdAndDeviceId(userId, deviceId)
        log.info("Device $deviceId removed for user $userId")
    }

    @Transactional
    fun updateLastActive(userId: UUID, deviceId: String) {
        val device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId) ?: return
        device.lastActiveAt = Instant.now()
        deviceRepository.save(device)
    }

    fun getDevicesWithPushToken(userId: UUID): List<Device> {
        return deviceRepository.findAllByUserId(userId).filter { it.pushToken != null }
    }
}
