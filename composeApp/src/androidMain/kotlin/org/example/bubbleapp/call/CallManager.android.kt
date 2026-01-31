package org.example.bubbleapp.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class CallManager {
    private val _callState = MutableStateFlow(CallState.IDLE)
    actual val callState: StateFlow<CallState> = _callState

    private val _currentCallerId = MutableStateFlow<String?>(null)
    actual val currentCallerId: StateFlow<String?> = _currentCallerId

    private val _currentCallerName = MutableStateFlow<String?>(null)
    actual val currentCallerName: StateFlow<String?> = _currentCallerName

    private val _isMuted = MutableStateFlow(false)
    actual val isMuted: StateFlow<Boolean> = _isMuted

    private val _isSpeakerOn = MutableStateFlow(false)
    actual val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn

    private val _errorMessage = MutableStateFlow<String?>(null)
    actual val errorMessage: StateFlow<String?> = _errorMessage

    private val _isConnected = MutableStateFlow(false)
    actual val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectionError = MutableStateFlow<String?>(null)
    actual val connectionError: StateFlow<String?> = _connectionError

    actual fun connect(serverUrl: String, userId: String, userName: String) {
        // TODO: Реализовать для Android
    }

    actual fun disconnect() {
        // TODO: Реализовать для Android
    }

    actual fun startCall(targetUserId: String) {
        // TODO: Реализовать для Android
    }

    actual fun acceptCall() {
        // TODO: Реализовать для Android
    }

    actual fun rejectCall() {
        // TODO: Реализовать для Android
    }

    actual fun endCall() {
        // TODO: Реализовать для Android
    }

    actual fun toggleMute() {
        // TODO: Реализовать для Android
    }

    actual fun toggleSpeaker() {
        // TODO: Реализовать для Android
    }

    actual fun setLocalIP(ip: String) {
        // TODO: Реализовать для Android
    }

    actual fun clearError() {
        _errorMessage.value = null
    }
}
