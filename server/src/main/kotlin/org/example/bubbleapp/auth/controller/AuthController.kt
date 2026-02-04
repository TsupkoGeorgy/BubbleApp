package org.example.bubbleapp.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.example.bubbleapp.auth.dto.models.*
import org.example.bubbleapp.auth.service.AuthService
import org.example.bubbleapp.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/send-code")
    @Operation(summary = "Send verification code", description = "Sends a verification code to the provided phone number")
    fun sendCode(
        @Valid @RequestBody request: SendCodeRequest
    ): ResponseEntity<SendCodeResponse> {
        val response = authService.sendCode(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify code", description = "Verifies the code and returns JWT tokens")
    fun verifyCode(
        @Valid @RequestBody request: VerifyCodeRequest
    ): ResponseEntity<AuthResponse> {
        val response = authService.verifyCode(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refreshes the access token using a refresh token")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<AuthResponse> {
        val response = authService.refreshToken(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidates the refresh token")
    fun logout(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestBody request: LogoutRequest
    ): ResponseEntity<LogoutResponse> {
        val response = authService.logout(user.userId, request)
        return ResponseEntity.ok(response)
    }
}
