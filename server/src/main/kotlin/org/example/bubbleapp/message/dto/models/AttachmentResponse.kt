package org.example.bubbleapp.message.dto.models

import java.util.UUID

data class AttachmentResponse(
    val id: UUID,
    val type: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val url: String?,
    val thumbnailUrl: String?,
    val durationMs: Int?,
    val width: Int?,
    val height: Int?
)
