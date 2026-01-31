package org.example.bubbleapp.call

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import platform.CallKit.CXCallEndedReasonRemoteEnded
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
actual class CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val signalingClient = SignalingClient(scope)
    private val callKitManager = CallKitManager()
    private val audioStreamer = AudioRelayStreamer(scope)

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

    actual val connectionError: StateFlow<String?> = signalingClient.connectionError

    private var myUserId: String? = null
    private var myUserName: String? = null
    private var currentCallUUID: NSUUID? = null
    private var pendingTargetUserId: String? = null

    actual fun setLocalIP(ip: String) {
        // Not needed for relay - server handles routing
    }

    init {
        setupSignalingListeners()
        setupCallKitListeners()
        setupAudioStreamerCallbacks()
        setupConnectionStateListener()
    }

    private fun setupConnectionStateListener() {
        signalingClient.connectionState.onEach { state ->
            _isConnected.value = (state == ConnectionState.CONNECTED)

            // Если соединение потеряно во время звонка - завершить звонок
            if (state == ConnectionState.FAILED || state == ConnectionState.DISCONNECTED) {
                if (_callState.value == CallState.ACTIVE || _callState.value == CallState.CONNECTING) {
                    println("Connection lost during call, ending call")
                    audioStreamer.stop()
                    callKitManager.endCall()
                    _callState.value = CallState.FAILED
                    _errorMessage.value = "Connection lost"
                    resetCallState()
                }
            }
        }.launchIn(scope)
    }

    private fun setupAudioStreamerCallbacks() {
        audioStreamer.onError = { error ->
            scope.launch {
                _errorMessage.value = error
                _callState.value = CallState.FAILED
            }
        }

        audioStreamer.onAudioData = { encryptedData ->
            // Send encrypted audio to peer via signaling only if connected
            if (_isConnected.value && _callState.value == CallState.ACTIVE) {
                val targetId = _currentCallerId.value ?: pendingTargetUserId
                if (targetId != null) {
                    signalingClient.send(SignalMessage.AudioData(targetId, encryptedData))
                }
            }
        }

        audioStreamer.onReady = {
            // Both keys are ready - start audio streaming
            scope.launch {
                if (_callState.value == CallState.CONNECTING) {
                    startAudioStreaming()
                }
            }
        }
    }

    actual fun connect(serverUrl: String, userId: String, userName: String) {
        myUserId = userId
        myUserName = userName
        signalingClient.connect(serverUrl, userId)
    }

    actual fun disconnect() {
        endCall()
        signalingClient.disconnect()
    }

    actual fun startCall(targetUserId: String) {
        if (_callState.value != CallState.IDLE) return

        pendingTargetUserId = targetUserId
        _callState.value = CallState.CALLING

        currentCallUUID = NSUUID()
        callKitManager.startOutgoingCall(currentCallUUID!!, targetUserId)

        signalingClient.send(
            SignalMessage.CallRequest(
                targetId = targetUserId,
                callerId = myUserId ?: "",
                callerName = myUserName ?: "Unknown"
            )
        )
    }

    actual fun acceptCall() {
        if (_callState.value != CallState.RINGING) return

        val callerId = _currentCallerId.value ?: return

        _callState.value = CallState.CONNECTING

        // Generate encryption key and send to peer
        val encryptionKey = audioStreamer.generateEncryptionKey()
        signalingClient.send(
            SignalMessage.EncryptionKey(
                targetId = callerId,
                key = encryptionKey
            )
        )

        // Send accept response
        signalingClient.send(
            SignalMessage.CallResponse(
                targetId = callerId,
                accepted = true
            )
        )
    }

    actual fun rejectCall() {
        if (_callState.value != CallState.RINGING) return

        val callerId = _currentCallerId.value ?: return

        signalingClient.send(
            SignalMessage.CallResponse(
                targetId = callerId,
                accepted = false
            )
        )

        callKitManager.endCall()
        resetCallState()
    }

    actual fun endCall() {
        val targetId = _currentCallerId.value ?: pendingTargetUserId

        if (targetId != null) {
            signalingClient.send(
                SignalMessage.CallEnd(
                    targetId = targetId,
                    reason = "user_ended"
                )
            )
        }

        audioStreamer.stop()
        callKitManager.endCall()
        resetCallState()
    }

    actual fun toggleMute() {
        _isMuted.value = !_isMuted.value
        audioStreamer.setMuted(_isMuted.value)
    }

    actual fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        audioStreamer.setSpeakerEnabled(_isSpeakerOn.value)
    }

    actual fun clearError() {
        _errorMessage.value = null
    }

    private fun setupSignalingListeners() {
        signalingClient.messages.onEach { message ->
            when (message) {
                is SignalMessage.CallRequest -> handleIncomingCall(message)
                is SignalMessage.CallResponse -> handleCallResponse(message)
                is SignalMessage.EncryptionKey -> handleEncryptionKey(message)
                is SignalMessage.AudioData -> handleAudioData(message)
                is SignalMessage.CallEnd -> handleCallEnd(message)
                is SignalMessage.Error -> handleError(message)
                else -> {}
            }
        }.launchIn(scope)
    }

    private fun setupCallKitListeners() {
        callKitManager.onAnswerCall = { uuid ->
            scope.launch {
                acceptCall()
            }
        }

        callKitManager.onEndCall = { uuid ->
            scope.launch {
                if (_callState.value != CallState.IDLE) {
                    endCall()
                }
            }
        }

        callKitManager.onMuteCall = { uuid, muted ->
            _isMuted.value = muted
            audioStreamer.setMuted(muted)
        }

        callKitManager.onStartCall = { uuid ->
            callKitManager.reportOutgoingCallStartedConnecting(uuid)
        }
    }

    private fun handleIncomingCall(message: SignalMessage.CallRequest) {
        if (_callState.value != CallState.IDLE) {
            signalingClient.send(
                SignalMessage.CallResponse(
                    targetId = message.callerId,
                    accepted = false
                )
            )
            return
        }

        _currentCallerId.value = message.callerId
        _currentCallerName.value = message.callerName
        _callState.value = CallState.RINGING

        currentCallUUID = NSUUID()
        callKitManager.reportIncomingCall(currentCallUUID!!, message.callerName)
    }

    private fun handleCallResponse(message: SignalMessage.CallResponse) {
        if (!message.accepted) {
            _callState.value = CallState.ENDED
            callKitManager.reportCallEnded(reason = CXCallEndedReasonRemoteEnded)
            resetCallState()
            return
        }

        _callState.value = CallState.CONNECTING
        _currentCallerId.value = message.targetId

        // Generate encryption key and send to peer
        val encryptionKey = audioStreamer.generateEncryptionKey()
        signalingClient.send(
            SignalMessage.EncryptionKey(
                targetId = message.targetId,
                key = encryptionKey
            )
        )
    }

    private fun handleEncryptionKey(message: SignalMessage.EncryptionKey) {
        println("Received encryption key from ${message.targetId}")
        audioStreamer.setRemoteEncryptionKey(message.key)
        // Audio will start via onReady callback when both keys are set
    }

    private fun handleAudioData(message: SignalMessage.AudioData) {
        // Decrypt and play received audio
        audioStreamer.receiveEncryptedAudio(message.data)
    }

    private fun startAudioStreaming() {
        println("Starting audio streaming with E2E encryption")
        audioStreamer.start()

        _callState.value = CallState.ACTIVE
        currentCallUUID?.let {
            callKitManager.reportOutgoingCallConnected(it)
        }
    }

    private fun handleCallEnd(message: SignalMessage.CallEnd) {
        _callState.value = CallState.ENDED
        callKitManager.reportCallEnded(reason = CXCallEndedReasonRemoteEnded)
        audioStreamer.stop()
        resetCallState()
    }

    private fun handleError(message: SignalMessage.Error) {
        println("Signaling error: ${message.message}")
        if (_callState.value == CallState.CALLING) {
            _callState.value = CallState.FAILED
            callKitManager.endCall()
            resetCallState()
        }
    }

    private fun resetCallState() {
        _callState.value = CallState.IDLE
        _currentCallerId.value = null
        _currentCallerName.value = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        currentCallUUID = null
        pendingTargetUserId = null
    }
}
