package org.example.bubbleapp.signaling

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class SignalingHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(SignalingHandler::class.java)

    // userId -> WebSocketSession
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // sessionId -> userId (для обратного маппинга при отключении)
    private val sessionToUser = ConcurrentHashMap<String, String>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("New connection: ${session.id}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = sessionToUser.remove(session.id)
        if (userId != null) {
            sessions.remove(userId)
            logger.info("User disconnected: $userId")
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val signal = objectMapper.readValue<SignalMessage>(message.payload)
            logger.debug("Received: $signal")

            when (signal) {
                is SignalMessage.Register -> handleRegister(session, signal)
                is SignalMessage.Offer -> forwardToTarget(session, signal)
                is SignalMessage.Answer -> forwardToTarget(session, signal)
                is SignalMessage.IceCandidate -> forwardToTarget(session, signal)
                is SignalMessage.CallRequest -> forwardToTarget(session, signal)
                is SignalMessage.CallResponse -> forwardToTarget(session, signal)
                is SignalMessage.CallEnd -> forwardToTarget(session, signal)
                is SignalMessage.AudioInfo -> forwardToTarget(session, signal)
                is SignalMessage.EncryptionKey -> forwardToTarget(session, signal)
                is SignalMessage.AudioData -> forwardToTarget(session, signal, logAudio = false)
                is SignalMessage.Error -> {} // клиент не должен слать ошибки
            }
        } catch (e: Exception) {
            logger.error("Error processing message: ${e.message}", e)
            sendError(session, "Invalid message format")
        }
    }

    private fun handleRegister(session: WebSocketSession, signal: SignalMessage.Register) {
        val userId = signal.userId

        // Проверить, не занят ли userId
        val existingSession = sessions[userId]
        if (existingSession != null && existingSession.isOpen && existingSession.id != session.id) {
            sendError(session, "User ID already in use")
            return
        }

        // Зарегистрировать пользователя
        sessions[userId] = session
        sessionToUser[session.id] = userId
        logger.info("User registered: $userId")
    }

    private fun forwardToTarget(session: WebSocketSession, signal: SignalMessage, logAudio: Boolean = true) {
        val targetId = signal.targetId ?: return
        val senderId = sessionToUser[session.id]

        if (senderId == null) {
            sendError(session, "Not registered")
            return
        }

        val targetSession = sessions[targetId]
        if (targetSession == null || !targetSession.isOpen) {
            // Don't send error for audio data - just drop silently
            if (signal !is SignalMessage.AudioData) {
                sendError(session, "Target user not found or offline")
            }
            return
        }

        // Добавить информацию об отправителе для некоторых сообщений
        val messageToSend = when (signal) {
            is SignalMessage.Offer -> signal.copy(targetId = senderId)
            is SignalMessage.Answer -> signal.copy(targetId = senderId)
            is SignalMessage.IceCandidate -> signal.copy(targetId = senderId)
            is SignalMessage.CallResponse -> signal.copy(targetId = senderId)
            is SignalMessage.CallEnd -> signal.copy(targetId = senderId)
            is SignalMessage.AudioInfo -> signal.copy(targetId = senderId)
            is SignalMessage.EncryptionKey -> signal.copy(targetId = senderId)
            is SignalMessage.AudioData -> signal.copy(targetId = senderId)
            else -> signal
        }

        sendMessage(targetSession, messageToSend)

        // Don't log audio data to avoid flooding logs
        if (logAudio) {
            logger.debug("Forwarded ${signal::class.simpleName} from $senderId to $targetId")
        }
    }

    private fun sendMessage(session: WebSocketSession, message: SignalMessage) {
        if (session.isOpen) {
            val json = objectMapper.writeValueAsString(message)
            session.sendMessage(TextMessage(json))
        }
    }

    private fun sendError(session: WebSocketSession, errorMessage: String) {
        sendMessage(session, SignalMessage.Error(errorMessage))
    }

    // Публичные методы для интеграции с push-уведомлениями
    fun isUserOnline(userId: String): Boolean {
        val session = sessions[userId]
        return session != null && session.isOpen
    }

    fun getOnlineUsers(): Set<String> {
        return sessions.filter { it.value.isOpen }.keys
    }

    fun getOnlineUsersWithInfo(): List<Map<String, String?>> {
        return sessions.filter { it.value.isOpen }.map { (userId, session) ->
            mapOf(
                "userId" to userId,
                "ip" to session.remoteAddress?.address?.hostAddress
            )
        }
    }
}
