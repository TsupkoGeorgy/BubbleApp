package org.example.bubbleapp.attachment.repository

import org.example.bubbleapp.attachment.entity.Attachment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AttachmentRepository : JpaRepository<Attachment, UUID> {
    fun findAllByMessageId(messageId: UUID): List<Attachment>
}
