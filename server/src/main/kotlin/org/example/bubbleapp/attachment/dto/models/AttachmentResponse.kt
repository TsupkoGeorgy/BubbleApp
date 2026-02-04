package org.example.bubbleapp.attachment.dto.models

import org.example.bubbleapp.attachment.entity.AttachmentType
import java.time.Instant
import java.util.UUID

data class AttachmentResponse(
    val id: UUID,
    val type: AttachmentType,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val downloadUrl: String?,
    val thumbnailUrl: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Int?,
    val createdAt: Instant
)
