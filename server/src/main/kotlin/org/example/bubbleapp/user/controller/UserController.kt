package org.example.bubbleapp.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.example.bubbleapp.attachment.service.S3StorageService
import org.example.bubbleapp.security.UserPrincipal
import org.example.bubbleapp.user.dto.UpdateProfileRequest
import org.example.bubbleapp.user.dto.UserResponse
import org.example.bubbleapp.user.dto.UserSearchResponse
import org.example.bubbleapp.user.service.UserService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User management endpoints")
class UserController(
    private val userService: UserService,
    private val s3StorageService: S3StorageService
) {

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    fun getCurrentUser(
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<UserResponse> {
        val response = userService.getUserById(user.userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile")
    fun updateProfile(
        @AuthenticationPrincipal user: UserPrincipal,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<UserResponse> {
        val response = userService.updateProfile(user.userId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload avatar for current user")
    fun uploadAvatar(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<UserResponse> {
        val avatarUrl = s3StorageService.uploadAvatar(user.userId, file)
        val response = userService.updateAvatar(user.userId, avatarUrl)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    fun getUserById(
        @PathVariable userId: UUID
    ): ResponseEntity<UserResponse> {
        val response = userService.getUserById(userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by phone number")
    fun searchUsers(
        @RequestParam phone: String
    ): ResponseEntity<UserSearchResponse> {
        val users = userService.searchByPhone(phone)
        return ResponseEntity.ok(UserSearchResponse(users = users, total = users.size))
    }
}
