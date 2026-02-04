package org.example.bubbleapp.message.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.example.bubbleapp.message.dto.models.*
import org.example.bubbleapp.message.service.MessageService
import org.example.bubbleapp.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Messages", description = "Message management endpoints")
class MessageController(
    private val messageService: MessageService
) {

    @PostMapping("/chats/{chatId}/messages")
    @Operation(summary = "Send a message to chat")
    fun sendMessage(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID,
        @Valid @RequestBody request: SendMessageRequest
    ): ResponseEntity<MessageResponse> {
        val response = messageService.sendMessage(chatId, user.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/chats/{chatId}/messages")
    @Operation(summary = "Get messages from chat")
    fun getMessages(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<MessageListResponse> {
        val response = messageService.getMessages(chatId, user.userId, page, size)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/messages/{messageId}")
    @Operation(summary = "Get message by ID")
    fun getMessage(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable messageId: UUID
    ): ResponseEntity<MessageResponse> {
        val response = messageService.getMessageById(messageId, user.userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/messages/{messageId}")
    @Operation(summary = "Edit message")
    fun editMessage(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable messageId: UUID,
        @Valid @RequestBody request: EditMessageRequest
    ): ResponseEntity<MessageResponse> {
        val response = messageService.editMessage(messageId, user.userId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete message")
    fun deleteMessage(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable messageId: UUID
    ): ResponseEntity<Void> {
        messageService.deleteMessage(messageId, user.userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/chats/{chatId}/read")
    @Operation(summary = "Mark messages as read")
    fun markAsRead(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID,
        @RequestParam untilMessageId: UUID
    ): ResponseEntity<Void> {
        messageService.markAsRead(chatId, user.userId, untilMessageId)
        return ResponseEntity.ok().build()
    }
}
