package org.example.bubbleapp.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SignalMessage {
    abstract val targetId: String?

    @Serializable
    @SerialName("register")
    data class Register(
        val userId: String,
        override val targetId: String? = null
    ) : SignalMessage()

    @Serializable
    @SerialName("offer")
    data class Offer(
        override val targetId: String,
        val sdp: String
    ) : SignalMessage()

    @Serializable
    @SerialName("answer")
    data class Answer(
        override val targetId: String,
        val sdp: String
    ) : SignalMessage()

    @Serializable
    @SerialName("ice_candidate")
    data class IceCandidate(
        override val targetId: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int
    ) : SignalMessage()

    @Serializable
    @SerialName("call_request")
    data class CallRequest(
        override val targetId: String,
        val callerId: String,
        val callerName: String
    ) : SignalMessage()

    @Serializable
    @SerialName("call_response")
    data class CallResponse(
        override val targetId: String,
        val accepted: Boolean
    ) : SignalMessage()

    @Serializable
    @SerialName("call_end")
    data class CallEnd(
        override val targetId: String,
        val reason: String = "ended"
    ) : SignalMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        override val targetId: String? = null
    ) : SignalMessage()

    @Serializable
    @SerialName("audio_info")
    data class AudioInfo(
        override val targetId: String,
        val ip: String,
        val port: Int
    ) : SignalMessage()
}

enum class CallState {
    IDLE,
    CALLING,      // исходящий звонок, ждём ответа
    RINGING,      // входящий звонок
    CONNECTING,   // WebRTC соединение устанавливается
    ACTIVE,       // разговор
    ENDED,
    FAILED
}
