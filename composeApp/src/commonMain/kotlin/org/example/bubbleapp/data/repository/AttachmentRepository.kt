package org.example.bubbleapp.data.repository

import org.example.bubbleapp.data.datasource.remote.AttachmentRemoteDataSource
import org.example.bubbleapp.data.model.Attachment

class AttachmentRepository(
    private val attachmentRemoteDataSource: AttachmentRemoteDataSource
) {
    suspend fun uploadVideo(
        localFilePath: String,
        fileName: String,
        fileSize: Long,
        durationMs: Int? = null,
        onProgress: (Float) -> Unit = {}
    ): Attachment {
        onProgress(0.1f)

        val mimeType = "video/mp4"
        val type = "VIDEO"

        val uploadResponse = attachmentRemoteDataSource.getUploadUrl(
            type = type,
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            durationMs = durationMs
        )

        onProgress(0.2f)

        val uploadSuccess = uploadFileToS3(
            uploadUrl = uploadResponse.uploadUrl,
            filePath = localFilePath,
            contentType = mimeType,
            onProgress = { progress ->
                onProgress(0.2f + progress * 0.7f)
            }
        )

        if (!uploadSuccess) {
            throw Exception("Ошибка загрузки файла")
        }

        onProgress(0.9f)

        val attachment = attachmentRemoteDataSource.confirmUpload(
            key = uploadResponse.key,
            type = type,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            durationMs = durationMs
        )

        onProgress(1.0f)

        return attachment
    }

    private suspend fun uploadFileToS3(
        uploadUrl: String,
        filePath: String,
        contentType: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        return uploadToS3(uploadUrl, filePath, contentType, onProgress)
    }
}

expect suspend fun uploadToS3(
    uploadUrl: String,
    filePath: String,
    contentType: String,
    onProgress: (Float) -> Unit
): Boolean
