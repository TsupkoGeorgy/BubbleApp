package org.example.bubbleapp.websocket.chat

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.example.bubbleapp.message.dto.MessageResponse
import java.time.Instant
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatWsMessage.NewChat::class, name = "NEW_CHAT"),
    JsonSubTypes.Type(value = ChatWsMessage.NewMessage::class, name = "NEW_MESSAGE"),
    JsonSubTypes.Type(value = ChatWsMessage.MessageEdited::class, name = "MESSAGE_EDITED"),
    JsonSubTypes.Type(value = ChatWsMessage.MessageDeleted::class, name = "MESSAGE_DELETED"),
    JsonSubTypes.Type(value = ChatWsMessage.Typing::class, name = "TYPING"),
    JsonSubTypes.Type(value = ChatWsMessage.StopTyping::class, name = "STOP_TYPING"),
    JsonSubTypes.Type(value = ChatWsMessage.UserOnline::class, name = "USER_ONLINE"),
    JsonSubTypes.Type(value = ChatWsMessage.UserOffline::class, name = "USER_OFFLINE"),
    JsonSubTypes.Type(value = ChatWsMessage.MessageRead::class, name = "MESSAGE_READ"),
    JsonSubTypes.Type(value = ChatWsMessage.Error::class, name = "ERROR")
)
sealed class ChatWsMessage {
    abstract val type: String

    data class NewChat(
        override val type: String = "NEW_CHAT",
        val chatId: UUID,
        val chatName: String?,
        val chatType: String,
        val createdBy: UUID
    ) : ChatWsMessage()

    data class NewMessage(
        override val type: String = "NEW_MESSAGE",
        val chatId: UUID,
        val message: MessageResponse
    ) : ChatWsMessage()

    data class MessageEdited(
        override val type: String = "MESSAGE_EDITED",
        val chatId: UUID,
        val messageId: UUID,
        val content: String,
        val updatedAt: Instant
    ) : ChatWsMessage()

    data class MessageDeleted(
        override val type: String = "MESSAGE_DELETED",
        val chatId: UUID,
        val messageId: UUID
    ) : ChatWsMessage()

    data class Typing(
        override val type: String = "TYPING",
        val chatId: UUID,
        val userId: UUID,
        val username: String?
    ) : ChatWsMessage()

    data class StopTyping(
        override val type: String = "STOP_TYPING",
        val chatId: UUID,
        val userId: UUID
    ) : ChatWsMessage()

    data class UserOnline(
        override val type: String = "USER_ONLINE",
        val userId: UUID,
        val username: String?
    ) : ChatWsMessage()

    data class UserOffline(
        override val type: String = "USER_OFFLINE",
        val userId: UUID
    ) : ChatWsMessage()

    data class MessageRead(
        override val type: String = "MESSAGE_READ",
        val chatId: UUID,
        val messageId: UUID,
        val userId: UUID,
        val readAt: Instant
    ) : ChatWsMessage()

    data class Error(
        override val type: String = "ERROR",
        val code: String,
        val message: String
    ) : ChatWsMessage()
}

// Incoming messages from client
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatWsAction.Subscribe::class, name = "SUBSCRIBE"),
    JsonSubTypes.Type(value = ChatWsAction.Unsubscribe::class, name = "UNSUBSCRIBE"),
    JsonSubTypes.Type(value = ChatWsAction.SendTyping::class, name = "TYPING"),
    JsonSubTypes.Type(value = ChatWsAction.SendStopTyping::class, name = "STOP_TYPING"),
    JsonSubTypes.Type(value = ChatWsAction.MarkRead::class, name = "MARK_READ")
)
sealed class ChatWsAction {
    abstract val action: String

    data class Subscribe(
        override val action: String = "SUBSCRIBE",
        val chatIds: List<UUID>
    ) : ChatWsAction()

    data class Unsubscribe(
        override val action: String = "UNSUBSCRIBE",
        val chatIds: List<UUID>
    ) : ChatWsAction()

    data class SendTyping(
        override val action: String = "TYPING",
        val chatId: UUID
    ) : ChatWsAction()

    data class SendStopTyping(
        override val action: String = "STOP_TYPING",
        val chatId: UUID
    ) : ChatWsAction()

    data class MarkRead(
        override val action: String = "MARK_READ",
        val chatId: UUID,
        val messageId: UUID
    ) : ChatWsAction()
}
