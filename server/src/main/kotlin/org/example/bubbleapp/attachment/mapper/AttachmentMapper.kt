package org.example.bubbleapp.attachment.mapper

import org.example.bubbleapp.attachment.dto.models.AttachmentResponse
import org.example.bubbleapp.attachment.entity.Attachment

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
