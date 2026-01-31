package org.example.bubbleapp.signaling

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SignalMessage.Offer::class, name = "offer"),
    JsonSubTypes.Type(value = SignalMessage.Answer::class, name = "answer"),
    JsonSubTypes.Type(value = SignalMessage.IceCandidate::class, name = "ice_candidate"),
    JsonSubTypes.Type(value = SignalMessage.CallRequest::class, name = "call_request"),
    JsonSubTypes.Type(value = SignalMessage.CallResponse::class, name = "call_response"),
    JsonSubTypes.Type(value = SignalMessage.CallEnd::class, name = "call_end"),
    JsonSubTypes.Type(value = SignalMessage.Register::class, name = "register"),
    JsonSubTypes.Type(value = SignalMessage.Error::class, name = "error"),
    JsonSubTypes.Type(value = SignalMessage.AudioInfo::class, name = "audio_info"),
    JsonSubTypes.Type(value = SignalMessage.EncryptionKey::class, name = "encryption_key"),
    JsonSubTypes.Type(value = SignalMessage.AudioData::class, name = "audio_data")
)
sealed class SignalMessage {
    abstract val targetId: String?

    data class Register(
        val userId: String,
        override val targetId: String? = null
    ) : SignalMessage()

    data class Offer(
        override val targetId: String,
        val sdp: String
    ) : SignalMessage()

    data class Answer(
        override val targetId: String,
        val sdp: String
    ) : SignalMessage()

    data class IceCandidate(
        override val targetId: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int
    ) : SignalMessage()

    data class CallRequest(
        override val targetId: String,
        val callerId: String,
        val callerName: String
    ) : SignalMessage()

    data class CallResponse(
        override val targetId: String,
        val accepted: Boolean
    ) : SignalMessage()

    data class CallEnd(
        override val targetId: String,
        val reason: String = "ended"
    ) : SignalMessage()

    data class Error(
        val message: String,
        override val targetId: String? = null
    ) : SignalMessage()

    data class AudioInfo(
        override val targetId: String,
        val ip: String,
        val port: Int
    ) : SignalMessage()

    data class EncryptionKey(
        override val targetId: String,
        val key: String  // Base64 encoded AES key
    ) : SignalMessage()

    data class AudioData(
        override val targetId: String,
        val data: String  // Base64 encoded encrypted audio
    ) : SignalMessage()
}
