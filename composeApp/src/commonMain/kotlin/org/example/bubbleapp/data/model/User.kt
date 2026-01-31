package org.example.bubbleapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val phone: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val lastSeen: String? = null,
    val isOnline: Boolean = false
)
