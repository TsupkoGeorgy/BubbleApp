package org.example.bubbleapp.message.service

import org.example.bubbleapp.chat.entity.Chat
import org.example.bubbleapp.chat.repository.ChatMemberRepository
import org.example.bubbleapp.chat.repository.ChatRepository
import org.example.bubbleapp.common.exception.EntityNotFoundException
import org.example.bubbleapp.message.dto.*
import org.example.bubbleapp.message.entity.Message
import org.example.bubbleapp.message.entity.MessageType
import org.example.bubbleapp.message.repository.MessageRepository
import org.example.bubbleapp.user.entity.User
import org.example.bubbleapp.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val chatMemberRepository: ChatMemberRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(MessageService::class.java)

    @Transactional
    fun sendMessage(chatId: UUID, senderId: UUID, request: SendMessageRequest): MessageResponse {
        val chat = findChatOrThrow(chatId)
        val sender = findUserOrThrow(senderId)
        validateMembership(chatId, senderId)

        // Validate message content
        if (request.type == MessageType.TEXT && request.content.isNullOrBlank()) {
            throw MessageValidationException("Text message must have content")
        }

        // Find reply-to message if specified
        val replyTo = request.replyToId?.let { replyId ->
            messageRepository.findById(replyId).orElseThrow {
                EntityNotFoundException("Reply-to message not found: $replyId")
            }.also {
                if (it.chat.id != chatId) {
                    throw MessageValidationException("Reply-to message must be in the same chat")
                }
            }
        }

        val message = Message(
            chat = chat,
            sender = sender,
            type = request.type,
            content = request.content,
            replyTo = replyTo
        )

        val savedMessage = messageRepository.save(message)

        // Update chat's updatedAt
        chatRepository.save(chat)

        log.info("Message ${savedMessage.id} sent in chat $chatId by user $senderId")

        return savedMessage.toResponse()
    }

    fun getMessages(
        chatId: UUID,
        userId: UUID,
        page: Int = 0,
        size: Int = 50
    ): MessageListResponse {
        findChatOrThrow(chatId)
        validateMembership(chatId, userId)

        val pageable = PageRequest.of(page, size)
        val messagesPage = messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable)

        val total = messageRepository.countByChatId(chatId)
        val hasMore = (page + 1) * size < total

        return MessageListResponse(
            messages = messagesPage.content.map { it.toResponse() },
            total = total,
            hasMore = hasMore
        )
    }

    fun getMessageById(messageId: UUID, userId: UUID): MessageResponse {
        val message = findMessageOrThrow(messageId)
        validateMembership(message.chat.id, userId)
        return message.toResponse()
    }

    @Transactional
    fun editMessage(messageId: UUID, userId: UUID, request: EditMessageRequest): MessageResponse {
        val message = findMessageOrThrow(messageId)

        // Only sender can edit
        if (message.sender.id != userId) {
            throw MessageAccessDeniedException("Only the sender can edit this message")
        }

        // Only text messages can be edited
        if (message.type != MessageType.TEXT) {
            throw MessageValidationException("Only text messages can be edited")
        }

        message.content = request.content
        val savedMessage = messageRepository.save(message)

        log.info("Message $messageId edited by user $userId")

        return savedMessage.toResponse()
    }

    @Transactional
    fun deleteMessage(messageId: UUID, userId: UUID) {
        val message = findMessageOrThrow(messageId)
        validateMembership(message.chat.id, userId)

        // Only sender can delete (or chat admin in future)
        if (message.sender.id != userId) {
            throw MessageAccessDeniedException("Only the sender can delete this message")
        }

        messageRepository.delete(message)
        log.info("Message $messageId deleted by user $userId")
    }

    private fun findChatOrThrow(chatId: UUID): Chat {
        return chatRepository.findById(chatId).orElseThrow {
            EntityNotFoundException("Chat not found: $chatId")
        }
    }

    private fun findUserOrThrow(userId: UUID): User {
        return userRepository.findById(userId).orElseThrow {
            EntityNotFoundException("User not found: $userId")
        }
    }

    private fun findMessageOrThrow(messageId: UUID): Message {
        return messageRepository.findById(messageId).orElseThrow {
            EntityNotFoundException("Message not found: $messageId")
        }
    }

    private fun validateMembership(chatId: UUID, userId: UUID) {
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw MessageAccessDeniedException("User is not a member of this chat")
        }
    }
}

class MessageValidationException(message: String) : RuntimeException(message)
class MessageAccessDeniedException(message: String) : RuntimeException(message)
