package org.example.bubbleapp.call

import kotlinx.coroutines.flow.StateFlow

expect class CallManager() {
    val callState: StateFlow<CallState>
    val currentCallerId: StateFlow<String?>
    val currentCallerName: StateFlow<String?>
    val isMuted: StateFlow<Boolean>
    val isSpeakerOn: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    fun connect(serverUrl: String, userId: String, userName: String)
    fun disconnect()

    fun startCall(targetUserId: String)
    fun acceptCall()
    fun rejectCall()
    fun endCall()

    fun toggleMute()
    fun toggleSpeaker()

    fun setLocalIP(ip: String)
    fun clearError()
}
