package org.example.bubbleapp.data.repository

import org.example.bubbleapp.data.datasource.remote.UserRemoteDataSource
import org.example.bubbleapp.data.model.User

class UserRepository(
    private val userRemoteDataSource: UserRemoteDataSource
) {
    private val userCache = mutableMapOf<String, User>()

    suspend fun searchByPhone(phone: String): List<User> {
        val users = userRemoteDataSource.searchUsers(phone)
        users.forEach { userCache[it.id] = it }
        return users
    }

    suspend fun getUser(userId: String): User {
        userCache[userId]?.let { return it }

        val user = userRemoteDataSource.getUser(userId)
        userCache[userId] = user
        return user
    }

    fun getCached(userId: String): User? = userCache[userId]
}
