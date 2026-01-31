package org.example.bubbleapp.device.repository

import org.example.bubbleapp.device.entity.Device
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceRepository : JpaRepository<Device, UUID> {
    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): Device?
    fun findAllByUserId(userId: UUID): List<Device>
    fun deleteByUserIdAndDeviceId(userId: UUID, deviceId: String)
}
