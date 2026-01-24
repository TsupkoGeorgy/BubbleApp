package org.example.bubbleapp.call

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.setActive
import platform.Foundation.NSError

/**
 * WebRTC клиент для аудио звонков.
 *
 * ВАЖНО: Для работы требуется подключить WebRTC.framework от Google.
 * Добавьте в Podfile: pod 'GoogleWebRTC', '~> 1.1'
 *
 * Этот класс использует заглушки для WebRTC API.
 * После подключения фреймворка замените заглушки на реальные вызовы.
 */
@OptIn(ExperimentalForeignApi::class)
class WebRTCClient(
    private val scope: CoroutineScope
) {
    // ICE серверы для NAT traversal
    private val iceServers = listOf(
        IceServerConfig("stun:stun.l.google.com:19302"),
        IceServerConfig("stun:stun1.l.google.com:19302")
        // Добавьте TURN сервер для продакшена:
        // IceServerConfig("turn:your-turn-server.com:3478", "username", "password")
    )

    private val _localIceCandidates = MutableSharedFlow<IceCandidateData>()
    val localIceCandidates: SharedFlow<IceCandidateData> = _localIceCandidates

    private val _connectionStateChanged = MutableSharedFlow<WebRTCConnectionState>()
    val connectionStateChanged: SharedFlow<WebRTCConnectionState> = _connectionStateChanged

    private var localSdp: String? = null
    private var remoteSdp: String? = null

    // Флаг: мы создаём offer (звоним) или answer (отвечаем)
    private var isInitiator = false

    init {
        setupAudioSession()
    }

    private fun setupAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = AVAudioSessionCategoryOptionAllowBluetooth or
                        AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null
            )
            audioSession.setActive(true, error = null)
        } catch (e: Exception) {
            println("Error setting up audio session: ${e.message}")
        }
    }

    /**
     * Создать offer (для инициатора звонка)
     */
    fun createOffer(callback: (sdp: String?) -> Unit) {
        isInitiator = true
        scope.launch {
            _connectionStateChanged.emit(WebRTCConnectionState.CONNECTING)
        }

        // TODO: Заменить на реальный WebRTC код после подключения фреймворка
        // val constraints = RTCMediaConstraints(...)
        // peerConnection.offer(for: constraints) { sdp, error in ... }

        // Заглушка - в реальности здесь будет SDP от WebRTC
        val fakeSdp = generateFakeSdp("offer")
        localSdp = fakeSdp
        callback(fakeSdp)
    }

    /**
     * Создать answer (для отвечающего на звонок)
     */
    fun createAnswer(callback: (sdp: String?) -> Unit) {
        isInitiator = false

        // TODO: Заменить на реальный WebRTC код
        // peerConnection.answer(for: constraints) { sdp, error in ... }

        val fakeSdp = generateFakeSdp("answer")
        localSdp = fakeSdp
        callback(fakeSdp)
    }

    /**
     * Установить remote SDP (от другого участника)
     */
    fun setRemoteDescription(sdp: String, type: SdpType, callback: (success: Boolean) -> Unit) {
        remoteSdp = sdp

        // TODO: Заменить на реальный WebRTC код
        // let sessionDescription = RTCSessionDescription(type: type, sdp: sdp)
        // peerConnection.setRemoteDescription(sessionDescription) { error in ... }

        callback(true)

        scope.launch {
            _connectionStateChanged.emit(WebRTCConnectionState.CONNECTED)
        }
    }

    /**
     * Добавить ICE candidate от удалённого участника
     */
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        // TODO: Заменить на реальный WebRTC код
        // let iceCandidate = RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpMLineIndex, sdpMid: sdpMid)
        // peerConnection.add(iceCandidate)

        println("Added ICE candidate: $candidate")
    }

    /**
     * Включить/выключить микрофон
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        // TODO: Заменить на реальный WebRTC код
        // localAudioTrack?.isEnabled = enabled
        println("Microphone ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Переключить на динамик/наушник
     */
    fun setSpeakerEnabled(enabled: Boolean) {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            if (enabled) {
                audioSession.overrideOutputAudioPort(
                    platform.AVFAudio.AVAudioSessionPortOverrideSpeaker,
                    error = null
                )
            } else {
                audioSession.overrideOutputAudioPort(
                    platform.AVFAudio.AVAudioSessionPortOverrideNone,
                    error = null
                )
            }
        } catch (e: Exception) {
            println("Error switching speaker: ${e.message}")
        }
    }

    /**
     * Закрыть соединение
     */
    fun close() {
        // TODO: Заменить на реальный WebRTC код
        // peerConnection.close()
        // factory.stopAecDump()

        localSdp = null
        remoteSdp = null

        scope.launch {
            _connectionStateChanged.emit(WebRTCConnectionState.DISCONNECTED)
        }

        // Деактивировать аудио сессию
        try {
            AVAudioSession.sharedInstance().setActive(false, error = null)
        } catch (e: Exception) {
            println("Error deactivating audio session: ${e.message}")
        }
    }

    // Вспомогательный метод для генерации тестового SDP
    private fun generateFakeSdp(type: String): String {
        return """
            v=0
            o=- 0 0 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE audio
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=rtcp:9 IN IP4 0.0.0.0
            a=ice-ufrag:fake
            a=ice-pwd:fakepassword
            a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00
            a=setup:${if (type == "offer") "actpass" else "active"}
            a=mid:audio
            a=sendrecv
            a=rtpmap:111 opus/48000/2
        """.trimIndent()
    }
}

data class IceServerConfig(
    val url: String,
    val username: String? = null,
    val password: String? = null
)

data class IceCandidateData(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)

enum class SdpType {
    OFFER,
    ANSWER
}

enum class WebRTCConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}
