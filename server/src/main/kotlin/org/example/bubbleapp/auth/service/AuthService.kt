package org.example.bubbleapp.auth.service

import org.example.bubbleapp.auth.dto.models.*
import org.example.bubbleapp.auth.exception.InvalidCodeException
import org.example.bubbleapp.auth.exception.InvalidTokenException
import org.example.bubbleapp.auth.entity.RefreshToken
import org.example.bubbleapp.auth.repository.RefreshTokenRepository
import org.example.bubbleapp.security.JwtService
import org.example.bubbleapp.user.entity.User
import org.example.bubbleapp.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    // In-memory storage for verification codes (mock implementation)
    // In production, use Redis or similar with TTL
    private val verificationCodes = mutableMapOf<String, String>()

    fun sendCode(request: SendCodeRequest): SendCodeResponse {
        val phone = normalizePhone(request.phone)

        // Mock: always use code "1234" for development
        val code = "1234"
        verificationCodes[phone] = code

        log.info("Verification code sent to $phone: $code (mock)")

        return SendCodeResponse(
            success = true,
            message = "Verification code sent"
        )
    }

    @Transactional
    fun verifyCode(request: VerifyCodeRequest): AuthResponse {
        val phone = normalizePhone(request.phone)

        // Verify code
        val storedCode = verificationCodes[phone]
        if (storedCode != request.code) {
            throw InvalidCodeException("Invalid verification code")
        }

        // Remove used code
        verificationCodes.remove(phone)

        // Find or create user
        val user = userRepository.findByPhone(phone) ?: run {
            val newUser = User(phone = phone)
            userRepository.save(newUser)
        }

        // Generate tokens
        val accessToken = jwtService.generateAccessToken(user.id, user.phone, user.username)
        val refreshTokenValue = jwtService.generateRefreshToken()

        // Save refresh token
        val refreshToken = RefreshToken(
            user = user,
            token = refreshTokenValue,
            deviceId = request.deviceId,
            expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs())
        )
        refreshTokenRepository.save(refreshToken)

        log.info("User ${user.id} authenticated successfully")

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshTokenValue,
            expiresIn = jwtService.getRefreshTokenExpirationMs() / 1000,
            user = user.toDto()
        )
    }

    @Transactional
    fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            ?: throw InvalidTokenException("Invalid refresh token")

        if (!refreshToken.isValid()) {
            throw InvalidTokenException("Refresh token expired or revoked")
        }

        val user = refreshToken.user

        // Revoke old refresh token
        refreshToken.revokedAt = Instant.now()
        refreshTokenRepository.save(refreshToken)

        // Generate new tokens
        val accessToken = jwtService.generateAccessToken(user.id, user.phone, user.username)
        val newRefreshTokenValue = jwtService.generateRefreshToken()

        val newRefreshToken = RefreshToken(
            user = user,
            token = newRefreshTokenValue,
            deviceId = refreshToken.deviceId,
            expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs())
        )
        refreshTokenRepository.save(newRefreshToken)

        log.info("Token refreshed for user ${user.id}")

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = newRefreshTokenValue,
            expiresIn = jwtService.getRefreshTokenExpirationMs() / 1000,
            user = user.toDto()
        )
    }

    @Transactional
    fun logout(userId: UUID, request: LogoutRequest): LogoutResponse {
        if (request.allDevices) {
            refreshTokenRepository.revokeAllByUserId(userId)
            log.info("All sessions revoked for user $userId")
            return LogoutResponse(success = true, message = "Logged out from all devices")
        }

        if (request.refreshToken != null) {
            val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            if (refreshToken != null && refreshToken.user.id == userId) {
                refreshToken.revokedAt = Instant.now()
                refreshTokenRepository.save(refreshToken)
            }
        }

        log.info("User $userId logged out")
        return LogoutResponse(success = true, message = "Logged out successfully")
    }

    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^+\\d]"), "")
    }

    private fun User.toDto() = UserDto(
        id = id,
        phone = phone,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl
    )
}
