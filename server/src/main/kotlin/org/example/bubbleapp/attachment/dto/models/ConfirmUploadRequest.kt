package org.example.bubbleapp.attachment.dto.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.example.bubbleapp.attachment.entity.AttachmentType
import java.util.UUID

data class ConfirmUploadRequest(
    @field:NotBlank(message = "S3 key is required")
    val key: String,

    val type: AttachmentType,

    @field:NotBlank(message = "File name is required")
    val fileName: String,

    @field:Positive(message = "File size must be positive")
    val fileSize: Long,

    @field:NotBlank(message = "MIME type is required")
    val mimeType: String,

    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,

    // Optional: link to message immediately
    val messageId: UUID? = null
)
