package org.example.bubbleapp.auth.dto.models

data class LogoutRequest(
    val refreshToken: String? = null,
    val allDevices: Boolean = false
)
