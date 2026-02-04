package org.example.bubbleapp.chat.mapper

import org.example.bubbleapp.chat.dto.models.ChatMemberResponse
import org.example.bubbleapp.chat.dto.models.ChatResponse
import org.example.bubbleapp.chat.entity.Chat
import org.example.bubbleapp.chat.entity.ChatMember

fun Chat.toResponse(): ChatResponse = ChatResponse(
    id = id,
    type = type,
    name = name,
    avatarUrl = avatarUrl,
    members = members.map { it.toResponse() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatMember.toResponse(): ChatMemberResponse = ChatMemberResponse(
    userId = user.id,
    phone = user.phone,
    username = user.username,
    displayName = user.displayName,
    avatarUrl = user.avatarUrl,
    role = role,
    joinedAt = joinedAt
)
