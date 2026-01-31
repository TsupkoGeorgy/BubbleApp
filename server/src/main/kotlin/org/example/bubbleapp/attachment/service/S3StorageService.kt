package org.example.bubbleapp.attachment.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
class S3StorageService(
    private val s3Client: S3Client?,
    private val s3Presigner: S3Presigner?,
    @Value("\${s3.bucket:bubbleapp}")
    private val bucket: String,
    @Value("\${s3.endpoint:http://localhost:9000}")
    private val endpoint: String
) {
    private val log = LoggerFactory.getLogger(S3StorageService::class.java)

    fun uploadAvatar(userId: UUID, file: MultipartFile): String {
        validateImageFile(file)

        val extension = file.originalFilename?.substringAfterLast('.', "jpg") ?: "jpg"
        val key = "avatars/$userId/${UUID.randomUUID()}.$extension"

        return uploadFile(key, file)
    }

    fun uploadAttachment(chatId: UUID, messageId: UUID, file: MultipartFile): String {
        val extension = file.originalFilename?.substringAfterLast('.', "bin") ?: "bin"
        val key = "attachments/$chatId/$messageId/${UUID.randomUUID()}.$extension"

        return uploadFile(key, file)
    }

    fun getPresignedUploadUrl(key: String, contentType: String): String {
        if (s3Presigner == null) {
            log.warn("S3 not configured, returning mock URL")
            return "$endpoint/$bucket/$key"
        }

        val request = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest { builder ->
                builder.bucket(bucket)
                    .key(key)
                    .contentType(contentType)
            }
            .build()

        return s3Presigner.presignPutObject(request).url().toString()
    }

    fun getPresignedDownloadUrl(key: String): String {
        if (s3Presigner == null) {
            log.warn("S3 not configured, returning mock URL")
            return "$endpoint/$bucket/$key"
        }

        val request = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1))
            .getObjectRequest { builder ->
                builder.bucket(bucket).key(key)
            }
            .build()

        return s3Presigner.presignGetObject(request).url().toString()
    }

    private fun uploadFile(key: String, file: MultipartFile): String {
        if (s3Client == null) {
            log.warn("S3 not configured, returning mock URL for key: $key")
            return "$endpoint/$bucket/$key"
        }

        val putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.contentType ?: "application/octet-stream")
            .build()

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.inputStream, file.size))
        log.info("Uploaded file to S3: $key")

        return "$endpoint/$bucket/$key"
    }

    private fun validateImageFile(file: MultipartFile) {
        val allowedTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
        if (file.contentType !in allowedTypes) {
            throw InvalidFileException("Invalid file type. Allowed: JPEG, PNG, GIF, WebP")
        }

        val maxSize = 5 * 1024 * 1024 // 5MB
        if (file.size > maxSize) {
            throw InvalidFileException("File too large. Maximum size: 5MB")
        }
    }
}

class InvalidFileException(message: String) : RuntimeException(message)
