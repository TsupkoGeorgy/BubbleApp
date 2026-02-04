package org.example.bubbleapp.data.datasource.remote

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.model.User

class UserRemoteDataSource(
    private val apiClient: ApiClient
) {
    suspend fun searchUsers(query: String): List<User> {
        return apiClient.searchUsers(query)
    }

    suspend fun getUser(userId: String): User {
        return apiClient.getUser(userId)
    }
}
