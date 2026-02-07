package org.example.bubbleapp.ui.profile

import org.example.bubbleapp.data.model.User

data class UserProfileViewState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val existingChatId: String? = null
)
