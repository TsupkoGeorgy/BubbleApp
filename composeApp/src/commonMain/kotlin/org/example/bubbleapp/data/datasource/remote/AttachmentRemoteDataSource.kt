package org.example.bubbleapp.data.datasource.remote

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.ConfirmUploadRequest
import org.example.bubbleapp.data.api.UploadUrlRequest
import org.example.bubbleapp.data.api.UploadUrlResponse
import org.example.bubbleapp.data.model.Attachment

class AttachmentRemoteDataSource(
    private val apiClient: ApiClient
) {
    suspend fun getUploadUrl(
        type: String,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        durationMs: Int?
    ): UploadUrlResponse {
        return apiClient.getUploadUrl(
            UploadUrlRequest(
                type = type,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                durationMs = durationMs
            )
        )
    }

    suspend fun confirmUpload(
        key: String,
        type: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        durationMs: Int?
    ): Attachment {
        return apiClient.confirmUpload(
            ConfirmUploadRequest(
                key = key,
                type = type,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                durationMs = durationMs
            )
        )
    }
}
