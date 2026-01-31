package org.example.bubbleapp.chat.service

import org.example.bubbleapp.chat.dto.*
import org.example.bubbleapp.chat.entity.Chat
import org.example.bubbleapp.chat.entity.ChatMember
import org.example.bubbleapp.chat.entity.ChatType
import org.example.bubbleapp.chat.entity.MemberRole
import org.example.bubbleapp.chat.repository.ChatMemberRepository
import org.example.bubbleapp.chat.repository.ChatRepository
import org.example.bubbleapp.common.exception.EntityNotFoundException
import org.example.bubbleapp.user.entity.User
import org.example.bubbleapp.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatMemberRepository: ChatMemberRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)

    @Transactional
    fun createChat(currentUserId: UUID, request: CreateChatRequest): ChatResponse {
        val currentUser = findUserOrThrow(currentUserId)

        // For direct chats, check if one already exists
        if (request.type == ChatType.DIRECT && request.memberIds.size == 1) {
            val existingChat = findExistingDirectChat(currentUserId, request.memberIds.first())
            if (existingChat != null) {
                return existingChat.toResponse()
            }
        }

        val chat = Chat(
            type = request.type,
            name = if (request.type == ChatType.GROUP) request.name else null
        )

        // Add creator as owner
        val ownerMember = ChatMember(
            chat = chat,
            user = currentUser,
            role = if (request.type == ChatType.GROUP) MemberRole.OWNER else MemberRole.MEMBER
        )
        chat.members.add(ownerMember)

        // Add other members
        request.memberIds.forEach { memberId ->
            if (memberId != currentUserId) {
                val user = findUserOrThrow(memberId)
                val member = ChatMember(
                    chat = chat,
                    user = user,
                    role = MemberRole.MEMBER
                )
                chat.members.add(member)
            }
        }

        val savedChat = chatRepository.save(chat)
        log.info("Chat ${savedChat.id} created by user $currentUserId")

        return savedChat.toResponse()
    }

    fun getUserChats(userId: UUID): ChatListResponse {
        val chats = chatRepository.findAllByUserId(userId)
        return ChatListResponse(
            chats = chats.map { it.toResponse() },
            total = chats.size
        )
    }

    fun getChatById(chatId: UUID, currentUserId: UUID): ChatResponse {
        val chat = findChatOrThrow(chatId)
        validateMembership(chatId, currentUserId)
        return chat.toResponse()
    }

    @Transactional
    fun addMember(chatId: UUID, currentUserId: UUID, request: AddMemberRequest): ChatResponse {
        val chat = findChatOrThrow(chatId)
        validateMembership(chatId, currentUserId)

        if (chat.type == ChatType.DIRECT) {
            throw ChatOperationException("Cannot add members to direct chat")
        }

        // Check if user is already a member
        if (chatMemberRepository.existsByChatIdAndUserId(chatId, request.userId)) {
            throw ChatOperationException("User is already a member of this chat")
        }

        val user = findUserOrThrow(request.userId)
        val member = ChatMember(
            chat = chat,
            user = user,
            role = MemberRole.MEMBER
        )
        chat.members.add(member)

        val savedChat = chatRepository.save(chat)
        log.info("User ${request.userId} added to chat $chatId by $currentUserId")

        return savedChat.toResponse()
    }

    @Transactional
    fun removeMember(chatId: UUID, currentUserId: UUID, memberUserId: UUID): ChatResponse {
        val chat = findChatOrThrow(chatId)
        val currentMember = validateMembership(chatId, currentUserId)

        if (chat.type == ChatType.DIRECT) {
            throw ChatOperationException("Cannot remove members from direct chat")
        }

        // Only owner/admin can remove others, anyone can remove themselves
        if (currentUserId != memberUserId) {
            if (currentMember.role == MemberRole.MEMBER) {
                throw ChatOperationException("Only admins can remove other members")
            }
        }

        val memberToRemove = chatMemberRepository.findByChatIdAndUserId(chatId, memberUserId)
            ?: throw EntityNotFoundException("Member not found in chat")

        // Cannot remove owner
        if (memberToRemove.role == MemberRole.OWNER && currentUserId != memberUserId) {
            throw ChatOperationException("Cannot remove chat owner")
        }

        chat.members.remove(memberToRemove)
        chatMemberRepository.delete(memberToRemove)

        val savedChat = chatRepository.save(chat)
        log.info("User $memberUserId removed from chat $chatId by $currentUserId")

        return savedChat.toResponse()
    }

    @Transactional
    fun leaveChat(chatId: UUID, currentUserId: UUID) {
        val chat = findChatOrThrow(chatId)
        val member = validateMembership(chatId, currentUserId)

        if (chat.type == ChatType.DIRECT) {
            throw ChatOperationException("Cannot leave direct chat")
        }

        // If owner leaves, transfer ownership or delete chat
        if (member.role == MemberRole.OWNER) {
            val otherMembers = chat.members.filter { it.user.id != currentUserId }
            if (otherMembers.isNotEmpty()) {
                // Transfer ownership to first admin or member
                val newOwner = otherMembers.find { it.role == MemberRole.ADMIN }
                    ?: otherMembers.first()
                newOwner.role = MemberRole.OWNER
                chatMemberRepository.save(newOwner)
            }
        }

        chat.members.remove(member)
        chatMemberRepository.delete(member)

        // Delete chat if no members left
        if (chat.members.isEmpty()) {
            chatRepository.delete(chat)
            log.info("Chat $chatId deleted (no members left)")
        } else {
            chatRepository.save(chat)
        }

        log.info("User $currentUserId left chat $chatId")
    }

    @Transactional
    fun updateChat(chatId: UUID, currentUserId: UUID, request: UpdateChatRequest): ChatResponse {
        val chat = findChatOrThrow(chatId)
        val member = validateMembership(chatId, currentUserId)

        if (chat.type == ChatType.DIRECT) {
            throw ChatOperationException("Cannot update direct chat")
        }

        if (member.role == MemberRole.MEMBER) {
            throw ChatOperationException("Only admins can update chat settings")
        }

        request.name?.let { chat.name = it }

        val savedChat = chatRepository.save(chat)
        log.info("Chat $chatId updated by $currentUserId")

        return savedChat.toResponse()
    }

    private fun findExistingDirectChat(userId1: UUID, userId2: UUID): Chat? {
        val userChats = chatRepository.findAllByUserId(userId1)
        return userChats.find { chat ->
            chat.type == ChatType.DIRECT &&
            chat.members.size == 2 &&
            chat.members.any { it.user.id == userId2 }
        }
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

    private fun validateMembership(chatId: UUID, userId: UUID): ChatMember {
        return chatMemberRepository.findByChatIdAndUserId(chatId, userId)
            ?: throw ChatAccessDeniedException("User is not a member of this chat")
    }
}

class ChatOperationException(message: String) : RuntimeException(message)
class ChatAccessDeniedException(message: String) : RuntimeException(message)
