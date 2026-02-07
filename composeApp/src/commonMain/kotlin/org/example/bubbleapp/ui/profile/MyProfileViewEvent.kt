package org.example.bubbleapp.ui.profile

sealed class MyProfileViewEvent {
    data object NavigateBack : MyProfileViewEvent()
    data object LoggedOut : MyProfileViewEvent()
    data class ShowToast(val message: String) : MyProfileViewEvent()
}
