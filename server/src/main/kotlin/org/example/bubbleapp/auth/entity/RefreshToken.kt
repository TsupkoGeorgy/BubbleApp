package org.example.bubbleapp.auth.entity

import jakarta.persistence.*
import org.example.bubbleapp.user.entity.User
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, unique = true, length = 500)
    val token: String,

    @Column(name = "device_id", length = 255)
    val deviceId: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    fun isRevoked(): Boolean = revokedAt != null
    fun isValid(): Boolean = !isExpired() && !isRevoked()
}
