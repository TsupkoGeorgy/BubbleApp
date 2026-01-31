package org.example.bubbleapp.chat.repository

import org.example.bubbleapp.chat.entity.Chat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatRepository : JpaRepository<Chat, UUID> {
    @Query("SELECT c FROM Chat c JOIN c.members m WHERE m.user.id = :userId ORDER BY c.updatedAt DESC")
    fun findAllByUserId(userId: UUID): List<Chat>
}
