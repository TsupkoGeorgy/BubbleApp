package org.example.bubbleapp.chat.entity

import jakarta.persistence.*
import org.example.bubbleapp.user.entity.User
import java.time.Instant
import java.util.UUID

enum class MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}

@Entity
@Table(
    name = "chat_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["chat_id", "user_id"])]
)
class ChatMember(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    val chat: Chat,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: MemberRole = MemberRole.MEMBER,

    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now()
)
