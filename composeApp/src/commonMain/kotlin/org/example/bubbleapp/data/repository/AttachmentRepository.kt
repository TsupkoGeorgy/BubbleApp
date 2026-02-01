package org.example.bubbleapp.data.repository

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.ApiException
import org.example.bubbleapp.data.api.ConfirmUploadRequest
import org.example.bubbleapp.data.api.UploadUrlRequest
import org.example.bubbleapp.data.model.Attachment

data class UploadResult(
    val attachment: Attachment,
    val progress: Float
)

class AttachmentRepository(
    private val apiClient: ApiClient
) {
    /**
     * Upload a video file to S3 and return the attachment
     *
     * Flow:
     * 1. Get presigned URL from server
     * 2. Upload file to S3
     * 3. Confirm upload
     * 4. Return attachment with URL
     */
    suspend fun uploadVideo(
        localFilePath: String,
        fileName: String,
        fileSize: Long,
        durationMs: Int? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Attachment> {
        return try {
            onProgress(0.1f)

            val mimeType = "video/mp4"
            val type = "VIDEO"

            // 1. Get upload URL
            val uploadResponse = apiClient.getUploadUrl(
                UploadUrlRequest(
                    type = type,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    durationMs = durationMs
                )
            )

            onProgress(0.2f)

            // 2. Upload to S3
            val uploadSuccess = uploadFileToS3(
                uploadUrl = uploadResponse.uploadUrl,
                filePath = localFilePath,
                contentType = mimeType,
                onProgress = { progress ->
                    // Map 0-1 to 0.2-0.9
                    onProgress(0.2f + progress * 0.7f)
                }
            )

            if (!uploadSuccess) {
                return Result.failure(Exception("Ошибка загрузки файла"))
            }

            onProgress(0.9f)

            // 3. Confirm upload
            val attachment = apiClient.confirmUpload(
                ConfirmUploadRequest(
                    key = uploadResponse.key,
                    type = type,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    durationMs = durationMs
                )
            )

            onProgress(1.0f)

            Result.success(attachment)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Platform-specific file upload to S3
     * Implemented in iosMain/androidMain
     */
    private suspend fun uploadFileToS3(
        uploadUrl: String,
        filePath: String,
        contentType: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        return uploadToS3(uploadUrl, filePath, contentType, onProgress)
    }
}

/**
 * Platform-specific S3 upload
 */
expect suspend fun uploadToS3(
    uploadUrl: String,
    filePath: String,
    contentType: String,
    onProgress: (Float) -> Unit
): Boolean
