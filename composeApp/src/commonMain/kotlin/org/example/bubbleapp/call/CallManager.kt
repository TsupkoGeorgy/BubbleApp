package org.example.bubbleapp.call

import kotlinx.coroutines.flow.StateFlow

expect class CallManager() {
    val callState: StateFlow<CallState>
    val currentCallerId: StateFlow<String?>
    val currentCallerName: StateFlow<String?>
    val isMuted: StateFlow<Boolean>
    val isSpeakerOn: StateFlow<Boolean>

    fun connect(serverUrl: String, userId: String, userName: String)
    fun disconnect()

    fun startCall(targetUserId: String)
    fun acceptCall()
    fun rejectCall()
    fun endCall()

    fun toggleMute()
    fun toggleSpeaker()
}
