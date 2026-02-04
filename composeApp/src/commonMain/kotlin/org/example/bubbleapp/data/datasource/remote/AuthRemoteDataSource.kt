package org.example.bubbleapp.data.datasource.remote

import org.example.bubbleapp.data.api.ApiClient
import org.example.bubbleapp.data.api.AuthResponse
import org.example.bubbleapp.data.api.SendCodeResponse
import org.example.bubbleapp.data.api.UpdateProfileRequest
import org.example.bubbleapp.data.model.User

class AuthRemoteDataSource(
    private val apiClient: ApiClient
) {
    suspend fun sendCode(phone: String): SendCodeResponse {
        return apiClient.sendCode(phone)
    }

    suspend fun verifyCode(phone: String, code: String): AuthResponse {
        return apiClient.verifyCode(phone, code)
    }

    suspend fun refreshToken(refreshToken: String): AuthResponse {
        return apiClient.refreshToken(refreshToken)
    }

    suspend fun logout() {
        apiClient.logout()
    }

    suspend fun getMe(): User {
        return apiClient.getMe()
    }

    suspend fun updateProfile(username: String?, displayName: String?): User {
        return apiClient.updateProfile(
            UpdateProfileRequest(
                username = username,
                displayName = displayName
            )
        )
    }
}
