package org.example.bubbleapp.websocket.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.bubbleapp.chat.repository.ChatMemberRepository
import org.example.bubbleapp.security.UserPrincipal
import org.example.bubbleapp.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.example.bubbleapp.websocket.chat.models.ChatWsAction
import org.example.bubbleapp.websocket.chat.models.ChatWsMessage
import org.example.bubbleapp.message.dto.models.MessageResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class ChatWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val chatMemberRepository: ChatMemberRepository,
    private val userRepository: UserRepository
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)

    // userId -> session
    private val userSessions = ConcurrentHashMap<UUID, WebSocketSession>()

    // chatId -> set of userIds subscribed
    private val chatSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // sessionId -> userId
    private val sessionUsers = ConcurrentHashMap<String, UUID>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val user = session.attributes["user"] as? UserPrincipal
        if (user == null) {
            log.warn("Unauthenticated WebSocket connection attempt")
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        userSessions[user.userId] = session
        sessionUsers[session.id] = user.userId

        log.info("User ${user.userId} connected to chat WebSocket")

        // Notify others that user is online
        broadcastToUserContacts(user.userId, ChatWsMessage.UserOnline(
            userId = user.userId,
            username = user.userName
        ))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = sessionUsers.remove(session.id) ?: return
        userSessions.remove(userId)

        // Remove from all chat subscriptions
        chatSubscriptions.values.forEach { it.remove(userId) }

        log.info("User $userId disconnected from chat WebSocket")

        // Notify others that user is offline
        broadcastToUserContacts(userId, ChatWsMessage.UserOffline(userId = userId))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = sessionUsers[session.id] ?: return

        try {
            val action = objectMapper.readValue(message.payload, ChatWsAction::class.java)
            handleAction(userId, action, session)
        } catch (e: Exception) {
            log.error("Error handling WebSocket message", e)
            sendToSession(session, ChatWsMessage.Error(
                code = "INVALID_MESSAGE",
                message = "Failed to parse message"
            ))
        }
    }

    private fun handleAction(userId: UUID, action: ChatWsAction, session: WebSocketSession) {
        when (action) {
            is ChatWsAction.Subscribe -> {
                action.chatIds.forEach { chatId ->
                    if (chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
                        chatSubscriptions.computeIfAbsent(chatId) { ConcurrentHashMap.newKeySet() }.add(userId)
                        log.debug("User $userId subscribed to chat $chatId")
                    }
                }
            }
            is ChatWsAction.Unsubscribe -> {
                action.chatIds.forEach { chatId ->
                    chatSubscriptions[chatId]?.remove(userId)
                }
            }
            is ChatWsAction.SendTyping -> {
                val user = userRepository.findById(userId).orElse(null)
                broadcastToChat(action.chatId, ChatWsMessage.Typing(
                    chatId = action.chatId,
                    userId = userId,
                    username = user?.displayName ?: user?.username
                ), excludeUserId = userId)
            }
            is ChatWsAction.SendStopTyping -> {
                broadcastToChat(action.chatId, ChatWsMessage.StopTyping(
                    chatId = action.chatId,
                    userId = userId
                ), excludeUserId = userId)
            }
            is ChatWsAction.MarkRead -> {
                broadcastToChat(action.chatId, ChatWsMessage.MessageRead(
                    chatId = action.chatId,
                    messageId = action.messageId,
                    userId = userId,
                    readAt = java.time.Instant.now()
                ), excludeUserId = userId)
            }
        }
    }

    // Public methods for MessageService to call

    fun broadcastNewMessage(chatId: UUID, message: MessageResponse) {
        broadcastToChat(chatId, ChatWsMessage.NewMessage(chatId = chatId, message = message))
    }

    fun broadcastMessageEdited(chatId: UUID, messageId: UUID, content: String, updatedAt: java.time.Instant) {
        broadcastToChat(chatId, ChatWsMessage.MessageEdited(
            chatId = chatId,
            messageId = messageId,
            content = content,
            updatedAt = updatedAt
        ))
    }

    fun broadcastMessageDeleted(chatId: UUID, messageId: UUID) {
        broadcastToChat(chatId, ChatWsMessage.MessageDeleted(chatId = chatId, messageId = messageId))
    }

    fun broadcastNewChat(chatId: UUID, chatName: String?, chatType: String, createdBy: UUID, memberIds: List<UUID>) {
        // Send to all members of the new chat (except creator who already knows)
        memberIds.forEach { memberId ->
            if (memberId != createdBy) {
                userSessions[memberId]?.let { session ->
                    sendToSession(session, ChatWsMessage.NewChat(
                        chatId = chatId,
                        chatName = chatName,
                        chatType = chatType,
                        createdBy = createdBy
                    ))
                }
            }
        }
    }

    fun isUserOnline(userId: UUID): Boolean {
        return userSessions.containsKey(userId)
    }

    fun getOnlineUsers(): Set<UUID> {
        return userSessions.keys.toSet()
    }

    // Broadcast helpers

    fun broadcastToChat(chatId: UUID, message: ChatWsMessage, excludeUserId: UUID? = null) {
        val subscribedUsers = chatSubscriptions[chatId]
        log.info("Broadcasting to chat $chatId: ${message::class.simpleName}, subscribers: ${subscribedUsers?.size ?: 0}")

        if (subscribedUsers == null || subscribedUsers.isEmpty()) {
            log.warn("No subscribers for chat $chatId")
            return
        }

        subscribedUsers.forEach { userId ->
            if (userId != excludeUserId) {
                userSessions[userId]?.let { session ->
                    log.debug("Sending to user $userId")
                    sendToSession(session, message)
                }
            }
        }
    }

    private fun broadcastToUserContacts(userId: UUID, message: ChatWsMessage) {
        // Find all chats where user is a member and notify other members
        val userChats = chatSubscriptions.filter { it.value.contains(userId) }
        userChats.forEach { (_, users) ->
            users.forEach { otherUserId ->
                if (otherUserId != userId) {
                    userSessions[otherUserId]?.let { session ->
                        sendToSession(session, message)
                    }
                }
            }
        }
    }

    private fun sendToSession(session: WebSocketSession, message: ChatWsMessage) {
        try {
            if (session.isOpen) {
                val json = objectMapper.writeValueAsString(message)
                session.sendMessage(TextMessage(json))
            }
        } catch (e: Exception) {
            log.error("Error sending WebSocket message", e)
        }
    }
}
