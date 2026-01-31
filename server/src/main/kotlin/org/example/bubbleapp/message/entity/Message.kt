package org.example.bubbleapp.message.entity

import jakarta.persistence.*
import org.example.bubbleapp.attachment.entity.Attachment
import org.example.bubbleapp.chat.entity.Chat
import org.example.bubbleapp.user.entity.User
import java.time.Instant
import java.util.UUID

enum class MessageType {
    TEXT,
    VIDEO,
    IMAGE,
    VOICE,
    FILE
}

@Entity
@Table(name = "messages")
class Message(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    val chat: Chat,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: MessageType,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    var replyTo: Message? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "message", cascade = [CascadeType.ALL], orphanRemoval = true)
    val attachments: MutableList<Attachment> = mutableListOf()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
