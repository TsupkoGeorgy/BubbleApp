package org.example.bubbleapp.user.repository

import org.example.bubbleapp.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByPhone(phone: String): User?
    fun findByUsername(username: String): User?
    fun existsByPhone(phone: String): Boolean
    fun existsByUsername(username: String): Boolean
}
