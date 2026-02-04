package org.example.bubbleapp.attachment.dto.models

data class UploadUrlResponse(
    val uploadUrl: String,
    val key: String,
    val expiresIn: Int = 900 // 15 minutes
)
