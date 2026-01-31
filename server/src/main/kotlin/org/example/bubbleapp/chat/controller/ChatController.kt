package org.example.bubbleapp.chat.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.example.bubbleapp.chat.dto.*
import org.example.bubbleapp.chat.service.ChatService
import org.example.bubbleapp.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/chats")
@Tag(name = "Chats", description = "Chat management endpoints")
class ChatController(
    private val chatService: ChatService
) {

    @PostMapping
    @Operation(summary = "Create a new chat")
    fun createChat(
        @AuthenticationPrincipal user: UserPrincipal,
        @Valid @RequestBody request: CreateChatRequest
    ): ResponseEntity<ChatResponse> {
        val response = chatService.createChat(user.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    @Operation(summary = "Get all chats for current user")
    fun getMyChats(
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<ChatListResponse> {
        val response = chatService.getUserChats(user.userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{chatId}")
    @Operation(summary = "Get chat by ID")
    fun getChatById(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID
    ): ResponseEntity<ChatResponse> {
        val response = chatService.getChatById(chatId, user.userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{chatId}")
    @Operation(summary = "Update chat settings")
    fun updateChat(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID,
        @Valid @RequestBody request: UpdateChatRequest
    ): ResponseEntity<ChatResponse> {
        val response = chatService.updateChat(chatId, user.userId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{chatId}/members")
    @Operation(summary = "Add member to chat")
    fun addMember(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID,
        @Valid @RequestBody request: AddMemberRequest
    ): ResponseEntity<ChatResponse> {
        val response = chatService.addMember(chatId, user.userId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{chatId}/members/{userId}")
    @Operation(summary = "Remove member from chat")
    fun removeMember(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID,
        @PathVariable userId: UUID
    ): ResponseEntity<ChatResponse> {
        val response = chatService.removeMember(chatId, user.userId, userId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{chatId}/leave")
    @Operation(summary = "Leave chat")
    fun leaveChat(
        @AuthenticationPrincipal user: UserPrincipal,
        @PathVariable chatId: UUID
    ): ResponseEntity<Void> {
        chatService.leaveChat(chatId, user.userId)
        return ResponseEntity.noContent().build()
    }
}
