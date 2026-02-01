package org.example.bubbleapp.data.repository

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.ApiException
import org.example.bubbleapp.data.model.User

class UserRepository(
    private val apiClient: ApiClient
) {
    // Cache for user profiles
    private val userCache = mutableMapOf<String, User>()

    suspend fun searchByPhone(phone: String): Result<List<User>> {
        return try {
            val users = apiClient.searchUsers(phone)
            // Cache found users
            users.forEach { userCache[it.id] = it }
            Result.success(users)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String): Result<User> {
        // Check cache first
        userCache[userId]?.let { return Result.success(it) }

        return try {
            val user = apiClient.getUser(userId)
            userCache[userId] = user
            Result.success(user)
        } catch (e: ApiException) {
            Result.failure(Exception(e.error.error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCached(userId: String): User? = userCache[userId]
}
