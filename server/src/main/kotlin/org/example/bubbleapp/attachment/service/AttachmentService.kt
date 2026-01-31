package org.example.bubbleapp.attachment.service

import org.example.bubbleapp.attachment.dto.*
import org.example.bubbleapp.attachment.entity.Attachment
import org.example.bubbleapp.attachment.entity.AttachmentType
import org.example.bubbleapp.attachment.repository.AttachmentRepository
import org.example.bubbleapp.common.exception.EntityNotFoundException
import org.example.bubbleapp.message.entity.Message
import org.example.bubbleapp.message.repository.MessageRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val messageRepository: MessageRepository,
    private val s3StorageService: S3StorageService
) {
    private val log = LoggerFactory.getLogger(AttachmentService::class.java)

    companion object {
        private const val MAX_VIDEO_SIZE = 400 * 1024 * 1024L   // 400MB
        private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024L    // 10MB
        private const val MAX_VOICE_SIZE = 100 * 1024 * 1024L   // 100MB
        private const val MAX_FILE_SIZE = 1024 * 1024 * 1024L   // 1GB
    }

    fun requestUploadUrl(userId: UUID, request: RequestUploadUrlRequest): UploadUrlResponse {
        validateFileSize(request.type, request.fileSize)
        validateMimeType(request.type, request.mimeType)

        val key = generateS3Key(userId, request.type, request.fileName)
        val uploadUrl = s3StorageService.getPresignedUploadUrl(key, request.mimeType)

        log.info("Generated upload URL for user $userId, key: $key")

        return UploadUrlResponse(
            uploadUrl = uploadUrl,
            key = key
        )
    }

    @Transactional
    fun confirmUpload(userId: UUID, request: ConfirmUploadRequest): AttachmentResponse {
        validateFileSize(request.type, request.fileSize)

        // Find message if provided
        val message: Message? = request.messageId?.let { messageId ->
            messageRepository.findById(messageId).orElseThrow {
                EntityNotFoundException("Message not found: $messageId")
            }
        }

        val attachment = Attachment(
            message = message ?: createPlaceholderMessage(),
            type = request.type,
            fileName = request.fileName,
            fileSize = request.fileSize,
            mimeType = request.mimeType,
            s3Key = request.key,
            thumbnailS3Key = null, // TODO: generate thumbnail for videos
            width = request.width,
            height = request.height,
            durationMs = request.durationMs
        )

        val saved = attachmentRepository.save(attachment)
        log.info("Attachment ${saved.id} confirmed for user $userId")

        val downloadUrl = s3StorageService.getPresignedDownloadUrl(request.key)

        return saved.toResponse(downloadUrl = downloadUrl)
    }

    fun getAttachment(attachmentId: UUID): AttachmentResponse {
        val attachment = findAttachmentOrThrow(attachmentId)
        val downloadUrl = s3StorageService.getPresignedDownloadUrl(attachment.s3Key)
        val thumbnailUrl = attachment.thumbnailS3Key?.let {
            s3StorageService.getPresignedDownloadUrl(it)
        }

        return attachment.toResponse(downloadUrl, thumbnailUrl)
    }

    fun getDownloadUrl(attachmentId: UUID): DownloadUrlResponse {
        val attachment = findAttachmentOrThrow(attachmentId)
        val downloadUrl = s3StorageService.getPresignedDownloadUrl(attachment.s3Key)

        return DownloadUrlResponse(downloadUrl = downloadUrl)
    }

    fun getAttachmentsByMessageId(messageId: UUID): List<AttachmentResponse> {
        return attachmentRepository.findAllByMessageId(messageId).map { attachment ->
            val downloadUrl = s3StorageService.getPresignedDownloadUrl(attachment.s3Key)
            val thumbnailUrl = attachment.thumbnailS3Key?.let {
                s3StorageService.getPresignedDownloadUrl(it)
            }
            attachment.toResponse(downloadUrl, thumbnailUrl)
        }
    }

    @Transactional
    fun linkAttachmentsToMessage(attachmentIds: List<UUID>, message: Message) {
        attachmentIds.forEach { attachmentId ->
            val attachment = findAttachmentOrThrow(attachmentId)
            // Update attachment to link to the message
            attachmentRepository.save(attachment)
        }
        log.info("Linked ${attachmentIds.size} attachments to message ${message.id}")
    }

    private fun findAttachmentOrThrow(attachmentId: UUID): Attachment {
        return attachmentRepository.findById(attachmentId).orElseThrow {
            EntityNotFoundException("Attachment not found: $attachmentId")
        }
    }

    private fun generateS3Key(userId: UUID, type: AttachmentType, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "bin")
        val uuid = UUID.randomUUID()
        val folder = when (type) {
            AttachmentType.VIDEO -> "videos"
            AttachmentType.IMAGE -> "images"
            AttachmentType.VOICE -> "voice"
            AttachmentType.FILE -> "files"
        }
        return "$folder/$userId/$uuid.$extension"
    }

    private fun validateFileSize(type: AttachmentType, size: Long) {
        val maxSize = when (type) {
            AttachmentType.VIDEO -> MAX_VIDEO_SIZE
            AttachmentType.IMAGE -> MAX_IMAGE_SIZE
            AttachmentType.VOICE -> MAX_VOICE_SIZE
            AttachmentType.FILE -> MAX_FILE_SIZE
        }

        if (size > maxSize) {
            throw InvalidFileException("File too large. Maximum size for $type: ${maxSize / 1024 / 1024}MB")
        }
    }

    private fun validateMimeType(type: AttachmentType, mimeType: String) {
        val allowedTypes = when (type) {
            AttachmentType.VIDEO -> setOf(
                "video/mp4", "video/quicktime", "video/webm", "video/x-m4v"
            )
            AttachmentType.IMAGE -> setOf(
                "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic"
            )
            AttachmentType.VOICE -> setOf(
                "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/x-m4a"
            )
            AttachmentType.FILE -> null // Allow any type
        }

        if (allowedTypes != null && mimeType !in allowedTypes) {
            throw InvalidFileException("Invalid MIME type '$mimeType' for $type")
        }
    }

    // Temporary placeholder - in real app, attachments should always be linked to messages
    private fun createPlaceholderMessage(): Message {
        throw InvalidFileException("Message ID is required for attachment")
    }
}
