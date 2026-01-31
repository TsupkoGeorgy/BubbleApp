package org.example.bubbleapp.chat.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ChatType {
    DIRECT,
    GROUP
}

@Entity
@Table(name = "chats")
class Chat(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: ChatType,

    @Column(length = 100)
    var name: String? = null,

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "chat", cascade = [CascadeType.ALL], orphanRemoval = true)
    val members: MutableList<ChatMember> = mutableListOf()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
