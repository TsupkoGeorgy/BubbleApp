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
    private val webRTCClient = WebRTCClient(scope)

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

    private var myUserId: String? = null
    private var myUserName: String? = null
    private var currentCallUUID: NSUUID? = null
    private var pendingTargetUserId: String? = null

    init {
        setupSignalingListeners()
        setupCallKitListeners()
        setupWebRTCListeners()
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

        // Генерируем UUID для звонка
        currentCallUUID = NSUUID()

        // Показываем исходящий звонок в CallKit
        callKitManager.startOutgoingCall(currentCallUUID!!, targetUserId)

        // Отправляем запрос на сервер
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

        // Создаём answer и отправляем
        webRTCClient.createAnswer { sdp ->
            sdp?.let {
                signalingClient.send(
                    SignalMessage.Answer(
                        targetId = callerId,
                        sdp = it
                    )
                )
            }
        }
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

        callKitManager.endCall()
        webRTCClient.close()
        resetCallState()
    }

    actual fun toggleMute() {
        _isMuted.value = !_isMuted.value
        webRTCClient.setMicrophoneEnabled(!_isMuted.value)
    }

    actual fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        webRTCClient.setSpeakerEnabled(_isSpeakerOn.value)
    }

    private fun setupSignalingListeners() {
        signalingClient.messages.onEach { message ->
            when (message) {
                is SignalMessage.CallRequest -> handleIncomingCall(message)
                is SignalMessage.CallResponse -> handleCallResponse(message)
                is SignalMessage.Offer -> handleOffer(message)
                is SignalMessage.Answer -> handleAnswer(message)
                is SignalMessage.IceCandidate -> handleIceCandidate(message)
                is SignalMessage.CallEnd -> handleCallEnd(message)
                is SignalMessage.Error -> handleError(message)
                is SignalMessage.Register -> {} // игнорируем
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
            webRTCClient.setMicrophoneEnabled(!muted)
        }

        callKitManager.onStartCall = { uuid ->
            // Звонок начался через CallKit UI
            callKitManager.reportOutgoingCallStartedConnecting(uuid)
        }
    }

    private fun setupWebRTCListeners() {
        webRTCClient.localIceCandidates.onEach { candidate ->
            val targetId = _currentCallerId.value ?: pendingTargetUserId ?: return@onEach

            signalingClient.send(
                SignalMessage.IceCandidate(
                    targetId = targetId,
                    candidate = candidate.candidate,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            )
        }.launchIn(scope)

        webRTCClient.connectionStateChanged.onEach { state ->
            when (state) {
                WebRTCConnectionState.CONNECTED -> {
                    _callState.value = CallState.ACTIVE
                    currentCallUUID?.let {
                        callKitManager.reportOutgoingCallConnected(it)
                    }
                }
                WebRTCConnectionState.DISCONNECTED,
                WebRTCConnectionState.FAILED -> {
                    if (_callState.value == CallState.ACTIVE) {
                        _callState.value = CallState.ENDED
                        callKitManager.endCall()
                        resetCallState()
                    }
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    private fun handleIncomingCall(message: SignalMessage.CallRequest) {
        if (_callState.value != CallState.IDLE) {
            // Уже в звонке - отклоняем
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

        // Показываем входящий звонок через CallKit
        callKitManager.reportIncomingCall(currentCallUUID!!, message.callerName)
    }

    private fun handleCallResponse(message: SignalMessage.CallResponse) {
        if (!message.accepted) {
            _callState.value = CallState.ENDED
            callKitManager.reportCallEnded(reason = CXCallEndedReasonRemoteEnded)
            resetCallState()
            return
        }

        // Звонок принят - создаём offer
        _callState.value = CallState.CONNECTING

        webRTCClient.createOffer { sdp ->
            sdp?.let {
                val targetId = pendingTargetUserId ?: return@let
                signalingClient.send(
                    SignalMessage.Offer(
                        targetId = targetId,
                        sdp = it
                    )
                )
            }
        }
    }

    private fun handleOffer(message: SignalMessage.Offer) {
        _currentCallerId.value = message.targetId

        webRTCClient.setRemoteDescription(message.sdp, SdpType.OFFER) { success ->
            if (success) {
                webRTCClient.createAnswer { sdp ->
                    sdp?.let {
                        signalingClient.send(
                            SignalMessage.Answer(
                                targetId = message.targetId,
                                sdp = it
                            )
                        )
                    }
                }
            }
        }
    }

    private fun handleAnswer(message: SignalMessage.Answer) {
        webRTCClient.setRemoteDescription(message.sdp, SdpType.ANSWER) { success ->
            if (success) {
                _callState.value = CallState.ACTIVE
            }
        }
    }

    private fun handleIceCandidate(message: SignalMessage.IceCandidate) {
        webRTCClient.addIceCandidate(
            message.candidate,
            message.sdpMid,
            message.sdpMLineIndex
        )
    }

    private fun handleCallEnd(message: SignalMessage.CallEnd) {
        _callState.value = CallState.ENDED
        callKitManager.reportCallEnded(reason = CXCallEndedReasonRemoteEnded)
        webRTCClient.close()
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
