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
    private val audioStreamer = AudioStreamer()

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

    private var myUserId: String? = null
    private var myUserName: String? = null
    private var currentCallUUID: NSUUID? = null
    private var pendingTargetUserId: String? = null

    // Порты для UDP аудио
    private val localAudioPort = 5000 + (0..1000).random()
    private var remoteAudioInfo: Pair<String, Int>? = null
    private var myLocalIP: String? = null

    actual fun setLocalIP(ip: String) {
        myLocalIP = ip
    }

    init {
        setupSignalingListeners()
        setupCallKitListeners()
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

        // Отправляем accept
        signalingClient.send(
            SignalMessage.CallResponse(
                targetId = callerId,
                accepted = true
            )
        )

        // Отправляем свою аудио информацию
        sendAudioInfo(callerId)
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

    private fun sendAudioInfo(targetId: String) {
        // Получаем локальный IP
        val localIP = getLocalIPAddress() ?: "0.0.0.0"
        println("Sending audio info: IP=$localIP, port=$localAudioPort")

        signalingClient.send(
            SignalMessage.AudioInfo(
                targetId = targetId,
                ip = localIP,
                port = localAudioPort
            )
        )
    }

    private fun getLocalIPAddress(): String {
        // IP устанавливается через myLocalIP или берётся из настроек
        return myLocalIP ?: "0.0.0.0"
    }

    private fun startAudioStream() {
        val remote = remoteAudioInfo ?: return
        println("Starting audio stream to ${remote.first}:${remote.second}")

        audioStreamer.onError = { error ->
            println("Audio error: $error")
            scope.launch {
                _errorMessage.value = error
                _callState.value = CallState.FAILED
                audioStreamer.stop()
                callKitManager.endCall()
            }
        }

        try {
            audioStreamer.start(
                remoteHost = remote.first,
                remotePort = remote.second,
                localPort = localAudioPort
            )

            _callState.value = CallState.ACTIVE
            currentCallUUID?.let {
                callKitManager.reportOutgoingCallConnected(it)
            }
        } catch (e: Exception) {
            println("Failed to start audio stream: ${e.message}")
            _errorMessage.value = "Ошибка аудио: ${e.message}"
            _callState.value = CallState.FAILED
        }
    }

    private fun setupSignalingListeners() {
        signalingClient.messages.onEach { message ->
            when (message) {
                is SignalMessage.CallRequest -> handleIncomingCall(message)
                is SignalMessage.CallResponse -> handleCallResponse(message)
                is SignalMessage.AudioInfo -> handleAudioInfo(message)
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

        // Отправляем свою аудио информацию
        sendAudioInfo(message.targetId)
    }

    private fun handleAudioInfo(message: SignalMessage.AudioInfo) {
        println("Received audio info: IP=${message.ip}, port=${message.port}")
        remoteAudioInfo = Pair(message.ip, message.port)

        // Если мы уже в состоянии CONNECTING, начинаем стрим
        if (_callState.value == CallState.CONNECTING) {
            startAudioStream()
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
        remoteAudioInfo = null
    }
}
