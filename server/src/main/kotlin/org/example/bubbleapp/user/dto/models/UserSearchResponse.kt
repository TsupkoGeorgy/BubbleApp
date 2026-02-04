package org.example.bubbleapp.user.dto.models

data class UserSearchResponse(
    val users: List<UserResponse>,
    val total: Int
)
