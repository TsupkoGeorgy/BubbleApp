package org.example.bubbleapp.call

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.darwin.NSObject

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class SignalingClient(
    private val scope: CoroutineScope
) {
    private var webSocketTask: NSURLSessionWebSocketTask? = null
    private var session: NSURLSession? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messages = MutableSharedFlow<SignalMessage>()
    val messages: SharedFlow<SignalMessage> = _messages

    private var userId: String? = null
    private var pendingRegistration = false

    fun connect(serverUrl: String, userId: String) {
        this.userId = userId
        this.pendingRegistration = true
        _connectionState.value = ConnectionState.CONNECTING

        val url = NSURL.URLWithString(serverUrl) ?: run {
            _connectionState.value = ConnectionState.FAILED
            return
        }

        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
        session = NSURLSession.sessionWithConfiguration(
            configuration,
            delegate = WebSocketDelegate(),
            delegateQueue = NSOperationQueue.mainQueue
        )

        webSocketTask = session?.webSocketTaskWithURL(url)
        webSocketTask?.resume()

        receiveMessage()
    }

    private fun onWebSocketOpened() {
        println("WebSocket opened, sending registration for userId: $userId")
        if (pendingRegistration && userId != null) {
            pendingRegistration = false
            val registerMessage = SignalMessage.Register(userId!!)
            val jsonString = json.encodeToString<SignalMessage>(registerMessage)
            println("Sending register message: $jsonString")

            val wsMessage = NSURLSessionWebSocketMessage(jsonString)
            webSocketTask?.sendMessage(wsMessage) { error ->
                if (error != null) {
                    println("WebSocket send error: ${error.localizedDescription}")
                } else {
                    println("Register message sent successfully")
                    _connectionState.value = ConnectionState.CONNECTED
                }
            }
        }
    }

    fun disconnect() {
        webSocketTask?.cancelWithCloseCode(
            closeCode = 1000,
            reason = "User disconnected".encodeToByteArray().toNSData()
        )
        webSocketTask = null
        session = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun send(message: SignalMessage) {
        val jsonString = json.encodeToString(message)
        val wsMessage = NSURLSessionWebSocketMessage(jsonString)

        webSocketTask?.sendMessage(wsMessage) { error ->
            if (error != null) {
                println("WebSocket send error: ${error.localizedDescription}")
            }
        }
    }

    private fun receiveMessage() {
        webSocketTask?.receiveMessageWithCompletionHandler { message, error ->
            if (error != null) {
                println("WebSocket receive error: ${error.localizedDescription}")
                _connectionState.value = ConnectionState.FAILED
                return@receiveMessageWithCompletionHandler
            }

            message?.let { wsMessage ->
                wsMessage.string?.let { jsonString ->
                    try {
                        val signal = json.decodeFromString<SignalMessage>(jsonString)
                        scope.launch {
                            _messages.emit(signal)
                        }
                    } catch (e: Exception) {
                        println("Failed to parse message: $jsonString, error: ${e.message}")
                    }
                }
            }

            // Продолжаем слушать
            if (_connectionState.value == ConnectionState.CONNECTED) {
                receiveMessage()
            }
        }
    }

    private inner class WebSocketDelegate : NSObject(), NSURLSessionWebSocketDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didOpenWithProtocol: String?
        ) {
            println("WebSocket connected")
            onWebSocketOpened()
        }

        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didCloseWithCode: NSURLSessionWebSocketCloseCode,
            reason: NSData?
        ) {
            println("WebSocket closed with code: $didCloseWithCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun ByteArray.toNSData(): NSData {
        return NSString.create(string = this.decodeToString())
            .dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()
    }
}
