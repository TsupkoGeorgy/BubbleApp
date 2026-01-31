package org.example.bubbleapp.device.entity

import jakarta.persistence.*
import org.example.bubbleapp.user.entity.User
import java.time.Instant
import java.util.UUID

enum class Platform {
    IOS,
    ANDROID,
    WEB
}

@Entity
@Table(
    name = "devices",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "device_id"])]
)
class Device(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "device_id", nullable = false, length = 255)
    val deviceId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val platform: Platform,

    @Column(name = "push_token", length = 500)
    var pushToken: String? = null,

    @Column(name = "app_version", length = 20)
    var appVersion: String? = null,

    @Column(name = "os_version", length = 20)
    var osVersion: String? = null,

    @Column(name = "device_model", length = 100)
    var deviceModel: String? = null,

    @Column(name = "last_active_at", nullable = false)
    var lastActiveAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
