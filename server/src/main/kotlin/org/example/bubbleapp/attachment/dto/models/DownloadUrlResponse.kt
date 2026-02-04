package org.example.bubbleapp.attachment.dto.models

data class DownloadUrlResponse(
    val downloadUrl: String,
    val expiresIn: Int = 3600 // 1 hour
)
