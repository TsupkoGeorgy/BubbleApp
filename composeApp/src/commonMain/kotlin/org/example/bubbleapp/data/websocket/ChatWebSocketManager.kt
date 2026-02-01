package org.example.bubbleapp.data.websocket

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.bubbleapp.data.auth.TokenManager
import org.example.bubbleapp.data.model.Message

// ===== Incoming messages from server =====

@Serializable
sealed class ChatWsEvent {
    abstract val type: String
}

@Serializable
@SerialName("NEW_CHAT")
data class NewChatEvent(
    override val type: String = "NEW_CHAT",
    val chatId: String,
    val chatName: String? = null,
    val chatType: String,
    val createdBy: String
) : ChatWsEvent()

@Serializable
@SerialName("NEW_MESSAGE")
data class NewMessageEvent(
    override val type: String = "NEW_MESSAGE",
    val chatId: String,
    val message: Message
) : ChatWsEvent()

@Serializable
@SerialName("MESSAGE_EDITED")
data class MessageEditedEvent(
    override val type: String = "MESSAGE_EDITED",
    val chatId: String,
    val messageId: String,
    val content: String,
    val updatedAt: String
) : ChatWsEvent()

@Serializable
@SerialName("MESSAGE_DELETED")
data class MessageDeletedEvent(
    override val type: String = "MESSAGE_DELETED",
    val chatId: String,
    val messageId: String
) : ChatWsEvent()

@Serializable
@SerialName("TYPING")
data class TypingEvent(
    override val type: String = "TYPING",
    val chatId: String,
    val userId: String,
    val username: String? = null
) : ChatWsEvent()

@Serializable
@SerialName("STOP_TYPING")
data class StopTypingEvent(
    override val type: String = "STOP_TYPING",
    val chatId: String,
    val userId: String
) : ChatWsEvent()

@Serializable
@SerialName("USER_ONLINE")
data class UserOnlineEvent(
    override val type: String = "USER_ONLINE",
    val userId: String,
    val username: String? = null
) : ChatWsEvent()

@Serializable
@SerialName("USER_OFFLINE")
data class UserOfflineEvent(
    override val type: String = "USER_OFFLINE",
    val userId: String
) : ChatWsEvent()

@Serializable
@SerialName("MESSAGE_READ")
data class MessageReadEvent(
    override val type: String = "MESSAGE_READ",
    val chatId: String,
    val messageId: String,
    val userId: String,
    val readAt: String
) : ChatWsEvent()

@Serializable
@SerialName("ERROR")
data class WsErrorEvent(
    override val type: String = "ERROR",
    val code: String,
    val message: String
) : ChatWsEvent()

// ===== Outgoing actions to server =====

@Serializable
data class SubscribeAction(
    val action: String = "SUBSCRIBE",
    val chatIds: List<String>
)

@Serializable
data class UnsubscribeAction(
    val action: String = "UNSUBSCRIBE",
    val chatIds: List<String>
)

@Serializable
data class TypingAction(
    val action: String = "TYPING",
    val chatId: String
)

@Serializable
data class StopTypingAction(
    val action: String = "STOP_TYPING",
    val chatId: String
)

@Serializable
data class MarkReadAction(
    val action: String = "MARK_READ",
    val chatId: String,
    val messageId: String
)

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

class ChatWebSocketManager(
    private val wsUrl: String,
    private val tokenManager: TokenManager,
    private val scope: CoroutineScope
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val client = HttpClient {
        install(WebSockets)
    }

    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<ChatWsEvent>()
    val events: SharedFlow<ChatWsEvent> = _events.asSharedFlow()

    private val subscribedChats = mutableSetOf<String>()

    fun connect() {
        println("ChatWebSocket: connect() called, current state: ${_connectionState.value}")
        if (_connectionState.value == WsConnectionState.CONNECTING ||
            _connectionState.value == WsConnectionState.CONNECTED) {
            println("ChatWebSocket: already connecting/connected, skipping")
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionState.value = WsConnectionState.CONNECTING
            println("ChatWebSocket: connecting to $wsUrl")

            try {
                val token = tokenManager.getAccessToken()
                val urlWithAuth = if (token != null) {
                    "$wsUrl?token=$token"
                } else {
                    wsUrl
                }
                println("ChatWebSocket: URL with auth ready, token present: ${token != null}")

                client.webSocket(urlWithAuth) {
                    session = this
                    _connectionState.value = WsConnectionState.CONNECTED
                    println("ChatWebSocket: CONNECTED!")

                    // Re-subscribe to chats
                    if (subscribedChats.isNotEmpty()) {
                        println("ChatWebSocket: re-subscribing to ${subscribedChats.size} chats")
                        subscribe(subscribedChats.toList())
                    }

                    try {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    parseAndEmitEvent(text)
                                }
                                is Frame.Close -> {
                                    println("WebSocket closed: ${frame.readReason()}")
                                    break
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        println("WebSocket receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("WebSocket connection error: ${e.message}")
                _connectionState.value = WsConnectionState.FAILED
            }

            session = null
            _connectionState.value = WsConnectionState.DISCONNECTED

            // Auto-reconnect after delay
            delay(5000)
            if (connectionJob?.isActive == true) {
                connect()
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            session?.close()
            session = null
        }
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    fun subscribe(chatIds: List<String>) {
        println("ChatWebSocket: subscribe to chats: $chatIds")
        subscribedChats.addAll(chatIds)
        sendAction(SubscribeAction(chatIds = chatIds))
    }

    fun unsubscribe(chatIds: List<String>) {
        subscribedChats.removeAll(chatIds.toSet())
        sendAction(UnsubscribeAction(chatIds = chatIds))
    }

    fun sendTyping(chatId: String) {
        sendAction(TypingAction(chatId = chatId))
    }

    fun sendStopTyping(chatId: String) {
        sendAction(StopTypingAction(chatId = chatId))
    }

    fun sendMarkRead(chatId: String, messageId: String) {
        sendAction(MarkReadAction(chatId = chatId, messageId = messageId))
    }

    private inline fun <reified T> sendAction(action: T) {
        val session = this.session ?: return
        scope.launch {
            try {
                val jsonString = json.encodeToString(kotlinx.serialization.serializer<T>(), action)
                session.send(Frame.Text(jsonString))
            } catch (e: Exception) {
                println("WebSocket send error: ${e.message}")
            }
        }
    }

    private suspend fun parseAndEmitEvent(text: String) {
        println("ChatWebSocket: received message: ${text.take(200)}")
        try {
            // Parse type first to determine class
            val typeRegex = """"type"\s*:\s*"(\w+)"""".toRegex()
            val typeMatch = typeRegex.find(text)
            val type = typeMatch?.groupValues?.get(1)
            println("ChatWebSocket: event type: $type")

            val event: ChatWsEvent? = when (type) {
                "NEW_CHAT" -> json.decodeFromString<NewChatEvent>(text)
                "NEW_MESSAGE" -> json.decodeFromString<NewMessageEvent>(text)
                "MESSAGE_EDITED" -> json.decodeFromString<MessageEditedEvent>(text)
                "MESSAGE_DELETED" -> json.decodeFromString<MessageDeletedEvent>(text)
                "TYPING" -> json.decodeFromString<TypingEvent>(text)
                "STOP_TYPING" -> json.decodeFromString<StopTypingEvent>(text)
                "USER_ONLINE" -> json.decodeFromString<UserOnlineEvent>(text)
                "USER_OFFLINE" -> json.decodeFromString<UserOfflineEvent>(text)
                "MESSAGE_READ" -> json.decodeFromString<MessageReadEvent>(text)
                "ERROR" -> json.decodeFromString<WsErrorEvent>(text)
                else -> {
                    println("ChatWebSocket: Unknown event type: $type")
                    null
                }
            }

            event?.let {
                println("ChatWebSocket: emitting event: $type")
                _events.emit(it)
            }
        } catch (e: Exception) {
            println("ChatWebSocket: Failed to parse event: $text, error: ${e.message}")
        }
    }

    fun close() {
        disconnect()
        client.close()
    }
}
