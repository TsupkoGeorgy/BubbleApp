package org.example.bubbleapp.domain.usecase.message

import org.example.bubbleapp.data.model.Message
import org.example.bubbleapp.data.repository.AttachmentRepository
import org.example.bubbleapp.data.repository.MessageRepository

class SendVideoBubbleUseCase(
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository
) {
    suspend fun execute(
        chatId: String,
        localFileName: String,
        localFilePath: String?,
        fileSize: Long,
        durationMs: Int? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Message> {
        return try {
            val attachmentId: String?

            if (localFilePath != null && fileSize > 0) {
                val attachment = attachmentRepository.uploadVideo(
                    localFilePath = localFilePath,
                    fileName = localFileName,
                    fileSize = fileSize,
                    durationMs = durationMs,
                    onProgress = { progress ->
                        onProgress(progress * 0.8f)
                    }
                )
                attachmentId = attachment.id
            } else {
                attachmentId = null
            }

            onProgress(0.9f)

            val message = messageRepository.sendVideoBubbleMessage(
                chatId = chatId,
                content = if (attachmentId == null) localFileName else null,
                attachmentIds = if (attachmentId != null) listOf(attachmentId) else emptyList()
            )

            onProgress(1.0f)

            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
