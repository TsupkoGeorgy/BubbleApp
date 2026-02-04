package org.example.bubbleapp.message.dto.models

import jakarta.validation.constraints.Size
import org.example.bubbleapp.message.entity.MessageType
import java.util.UUID

data class SendMessageRequest(
    val type: MessageType = MessageType.TEXT,

    @field:Size(max = 10000, message = "Message content is too long")
    val content: String? = null,

    val replyToId: UUID? = null,

    // For attachments (will be used in Phase 5)
    val attachmentIds: List<UUID>? = null
)
