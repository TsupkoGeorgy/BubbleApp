package org.example.bubbleapp.websocket.chat.models

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

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
