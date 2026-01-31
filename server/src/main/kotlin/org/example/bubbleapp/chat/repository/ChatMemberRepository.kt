package org.example.bubbleapp.chat.repository

import org.example.bubbleapp.chat.entity.ChatMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatMemberRepository : JpaRepository<ChatMember, UUID> {
    fun findByChatIdAndUserId(chatId: UUID, userId: UUID): ChatMember?
    fun findAllByChatId(chatId: UUID): List<ChatMember>
    fun existsByChatIdAndUserId(chatId: UUID, userId: UUID): Boolean
}
