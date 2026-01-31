package org.example.bubbleapp.message.repository

import org.example.bubbleapp.message.entity.Message
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MessageRepository : JpaRepository<Message, UUID> {
    fun findByChatIdOrderByCreatedAtDesc(chatId: UUID, pageable: Pageable): Page<Message>
    fun countByChatId(chatId: UUID): Long
}
