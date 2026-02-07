package org.example.bubbleapp.ui.profile

sealed class UserProfileViewEvent {
    data class NavigateToChat(val chatId: String) : UserProfileViewEvent()
    data class NavigateToCall(val userId: String) : UserProfileViewEvent()
    data class ShowError(val message: String) : UserProfileViewEvent()
}
