package org.example.bubbleapp.attachment.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.example.bubbleapp.attachment.dto.models.*
import org.example.bubbleapp.attachment.service.AttachmentService
import org.example.bubbleapp.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/attachments")
@Tag(name = "Attachments", description = "File upload and download endpoints")
class AttachmentController(
    private val attachmentService: AttachmentService
) {

    @PostMapping("/upload-url")
    @Operation(summary = "Get presigned URL for file upload")
    fun requestUploadUrl(
        @AuthenticationPrincipal user: UserPrincipal,
        @Valid @RequestBody request: RequestUploadUrlRequest
    ): ResponseEntity<UploadUrlResponse> {
        val response = attachmentService.requestUploadUrl(user.userId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm file upload and create attachment record")
    fun confirmUpload(
        @AuthenticationPrincipal user: UserPrincipal,
        @Valid @RequestBody request: ConfirmUploadRequest
    ): ResponseEntity<AttachmentResponse> {
        val response = attachmentService.confirmUpload(user.userId, request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{attachmentId}")
    @Operation(summary = "Get attachment details")
    fun getAttachment(
        @PathVariable attachmentId: UUID
    ): ResponseEntity<AttachmentResponse> {
        val response = attachmentService.getAttachment(attachmentId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{attachmentId}/download-url")
    @Operation(summary = "Get presigned download URL")
    fun getDownloadUrl(
        @PathVariable attachmentId: UUID
    ): ResponseEntity<DownloadUrlResponse> {
        val response = attachmentService.getDownloadUrl(attachmentId)
        return ResponseEntity.ok(response)
    }
}
