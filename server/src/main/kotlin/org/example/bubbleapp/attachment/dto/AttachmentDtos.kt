package org.example.bubbleapp.attachment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.example.bubbleapp.attachment.entity.Attachment
import org.example.bubbleapp.attachment.entity.AttachmentType
import java.time.Instant
import java.util.UUID

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

data class UploadUrlResponse(
    val uploadUrl: String,
    val key: String,
    val expiresIn: Int = 900 // 15 minutes
)

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

data class DownloadUrlResponse(
    val downloadUrl: String,
    val expiresIn: Int = 3600 // 1 hour
)

fun Attachment.toResponse(downloadUrl: String? = null, thumbnailUrl: String? = null) = AttachmentResponse(
    id = id,
    type = type,
    fileName = fileName,
    fileSize = fileSize,
    mimeType = mimeType,
    downloadUrl = downloadUrl,
    thumbnailUrl = thumbnailUrl,
    width = width,
    height = height,
    durationMs = durationMs,
    createdAt = createdAt
)
