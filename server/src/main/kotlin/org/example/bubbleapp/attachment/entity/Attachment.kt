package org.example.bubbleapp.attachment.entity

import jakarta.persistence.*
import org.example.bubbleapp.message.entity.Message
import java.time.Instant
import java.util.UUID

enum class AttachmentType {
    VIDEO,
    IMAGE,
    VOICE,
    FILE
}

@Entity
@Table(name = "attachments")
class Attachment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    val message: Message,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: AttachmentType,

    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    @Column(name = "file_size", nullable = false)
    val fileSize: Long,

    @Column(name = "mime_type", nullable = false, length = 100)
    val mimeType: String,

    @Column(name = "s3_key", nullable = false, length = 500)
    val s3Key: String,

    @Column(name = "thumbnail_s3_key", length = 500)
    val thumbnailS3Key: String? = null,

    @Column(name = "duration_ms")
    val durationMs: Int? = null,

    @Column
    val width: Int? = null,

    @Column
    val height: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
