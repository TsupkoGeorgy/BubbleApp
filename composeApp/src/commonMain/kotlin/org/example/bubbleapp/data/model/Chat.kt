package org.example.bubbleapp.data.model

import kotlinx.serialization.Serializable

enum class ChatType {
    DIRECT,
    GROUP
}

@Serializable
data class Chat(
    val id: String,
    val type: String, // "PRIVATE" or "GROUP"
    val name: String? = null,
    val avatarUrl: String? = null,
    val members: List<ChatMember> = emptyList(),
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val createdAt: String? = null
)

@Serializable
data class ChatMember(
    val userId: String,
    val phone: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val role: String, // "OWNER", "ADMIN", "MEMBER"
    val joinedAt: String? = null
) {
    /** Получить отображаемое имя участника */
    val name: String
        get() = displayName ?: username ?: phone ?: "Unknown"
}
