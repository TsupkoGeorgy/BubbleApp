package org.example.bubbleapp.attachment.dto.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.example.bubbleapp.attachment.entity.AttachmentType

data class RequestUploadUrlRequest(
    val type: AttachmentType,

    @field:NotBlank(message = "File name is required")
    val fileName: String,

    @field:NotBlank(message = "MIME type is required")
    val mimeType: String,

    @field:Positive(message = "File size must be positive")
    val fileSize: Long,

    // For video/image
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null
)
