package org.example.bubbleapp.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class SendCodeRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Invalid phone number format")
    val phone: String
)

data class SendCodeResponse(
    val success: Boolean,
    val message: String
)

data class VerifyCodeRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Invalid phone number format")
    val phone: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Pattern(regexp = "^\\d{4}$", message = "Code must be 4 digits")
    val code: String,

    val deviceId: String? = null
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto
)

data class UserDto(
    val id: UUID,
    val phone: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String? = null,
    val allDevices: Boolean = false
)

data class LogoutResponse(
    val success: Boolean,
    val message: String
)
