package org.example.bubbleapp.user.service

import org.example.bubbleapp.common.exception.EntityNotFoundException
import org.example.bubbleapp.user.dto.UpdateProfileRequest
import org.example.bubbleapp.user.dto.UserResponse
import org.example.bubbleapp.user.dto.toResponse
import org.example.bubbleapp.user.entity.User
import org.example.bubbleapp.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    fun getUserById(userId: UUID): UserResponse {
        val user = findUserOrThrow(userId)
        return user.toResponse()
    }

    fun getUserByPhone(phone: String): UserResponse? {
        return userRepository.findByPhone(phone)?.toResponse()
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserResponse {
        val user = findUserOrThrow(userId)

        request.username?.let { newUsername ->
            if (newUsername != user.username) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw UsernameAlreadyExistsException("Username '$newUsername' is already taken")
                }
                user.username = newUsername
            }
        }

        request.displayName?.let { user.displayName = it }

        val updated = userRepository.save(user)
        log.info("User ${user.id} profile updated")
        return updated.toResponse()
    }

    @Transactional
    fun updateAvatar(userId: UUID, avatarUrl: String): UserResponse {
        val user = findUserOrThrow(userId)
        user.avatarUrl = avatarUrl
        val updated = userRepository.save(user)
        log.info("User ${user.id} avatar updated")
        return updated.toResponse()
    }

    fun searchByPhone(phone: String): List<UserResponse> {
        val normalizedPhone = phone.replace(Regex("[^+\\d]"), "")
        val user = userRepository.findByPhone(normalizedPhone)
        return if (user != null) listOf(user.toResponse()) else emptyList()
    }

    private fun findUserOrThrow(userId: UUID): User {
        return userRepository.findById(userId).orElseThrow {
            EntityNotFoundException("User not found: $userId")
        }
    }
}

class UsernameAlreadyExistsException(message: String) : RuntimeException(message)
