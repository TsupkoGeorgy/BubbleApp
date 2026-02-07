package org.example.bubbleapp.ui.profile

import org.example.bubbleapp.data.model.User

data class MyProfileViewState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val editedDisplayName: String = "",
    val editedUsername: String = "",
    val hasChanges: Boolean = false,
    val errorMessage: String? = null,
    val validationErrors: ValidationErrors = ValidationErrors(),
    val showImagePicker: Boolean = false,
    val showLogoutConfirmation: Boolean = false
)

data class ValidationErrors(
    val displayNameError: String? = null,
    val usernameError: String? = null
)
