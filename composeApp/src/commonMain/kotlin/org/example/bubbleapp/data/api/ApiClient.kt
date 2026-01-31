package org.example.bubbleapp.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.bubbleapp.data.auth.TokenManager
import org.example.bubbleapp.data.model.*

class ApiClient(
    private val baseUrl: String,
    private val tokenManager: TokenManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                override fun log(message: String) {
                    println("HTTP: $message")
                }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun HttpResponse.checkError(): HttpResponse {
        if (status.value >= 400) {
            val error = try {
                body<ApiError>()
            } catch (e: Exception) {
                ApiError(error = "Request failed: ${status.value}")
            }
            throw ApiException(status.value, error)
        }
        return this
    }

    private fun HttpRequestBuilder.auth() {
        tokenManager.getAccessToken()?.let { token ->
            header("Authorization", "Bearer $token")
        }
    }

    // ===== AUTH =====

    suspend fun sendCode(phone: String): SendCodeResponse {
        return client.post("$baseUrl/auth/send-code") {
            setBody(SendCodeRequest(phone))
        }.checkError().body()
    }

    suspend fun verifyCode(phone: String, code: String): AuthResponse {
        return client.post("$baseUrl/auth/verify") {
            setBody(VerifyCodeRequest(phone, code))
        }.checkError().body()
    }

    suspend fun refreshToken(refreshToken: String): AuthResponse {
        return client.post("$baseUrl/auth/refresh") {
            setBody(RefreshTokenRequest(refreshToken))
        }.checkError().body()
    }

    suspend fun logout() {
        client.post("$baseUrl/auth/logout") {
            auth()
        }
    }

    // ===== USER =====

    suspend fun getMe(): User {
        return client.get("$baseUrl/users/me") {
            auth()
        }.checkError().body()
    }

    suspend fun updateProfile(request: UpdateProfileRequest): User {
        return client.patch("$baseUrl/users/me") {
            auth()
            setBody(request)
        }.checkError().body()
    }

    suspend fun getUser(userId: String): User {
        return client.get("$baseUrl/users/$userId") {
            auth()
        }.checkError().body()
    }

    suspend fun searchUsers(query: String): List<User> {
        val response: UsersSearchResponse = client.get("$baseUrl/users/search") {
            auth()
            parameter("phone", query)
        }.checkError().body()
        return response.users
    }

    // ===== CHAT =====

    suspend fun getChats(): List<Chat> {
        val response: ChatsResponse = client.get("$baseUrl/chats") {
            auth()
        }.checkError().body()
        return response.chats
    }

    suspend fun getChat(chatId: String): Chat {
        return client.get("$baseUrl/chats/$chatId") {
            auth()
        }.checkError().body()
    }

    suspend fun createChat(request: CreateChatRequest): Chat {
        return client.post("$baseUrl/chats") {
            auth()
            setBody(request)
        }.checkError().body()
    }

    suspend fun deleteChat(chatId: String) {
        client.delete("$baseUrl/chats/$chatId") {
            auth()
        }.checkError()
    }

    // ===== MESSAGE =====

    suspend fun getMessages(chatId: String, before: String? = null, limit: Int = 50): List<Message> {
        return client.get("$baseUrl/chats/$chatId/messages") {
            auth()
            before?.let { parameter("before", it) }
            parameter("limit", limit)
        }.checkError().body()
    }

    suspend fun sendMessage(chatId: String, request: SendMessageRequest): Message {
        return client.post("$baseUrl/chats/$chatId/messages") {
            auth()
            setBody(request)
        }.checkError().body()
    }

    suspend fun editMessage(messageId: String, content: String): Message {
        return client.put("$baseUrl/messages/$messageId") {
            auth()
            setBody(EditMessageRequest(content))
        }.checkError().body()
    }

    suspend fun deleteMessage(messageId: String) {
        client.delete("$baseUrl/messages/$messageId") {
            auth()
        }.checkError()
    }

    suspend fun markAsRead(chatId: String, messageId: String) {
        client.post("$baseUrl/chats/$chatId/read") {
            auth()
            parameter("untilMessageId", messageId)
        }.checkError()
    }

    // ===== ATTACHMENT =====

    suspend fun getUploadUrl(request: UploadUrlRequest): UploadUrlResponse {
        return client.post("$baseUrl/attachments/upload-url") {
            auth()
            setBody(request)
        }.checkError().body()
    }

    suspend fun confirmUpload(attachmentId: String, request: ConfirmUploadRequest): Attachment {
        return client.post("$baseUrl/attachments/$attachmentId/confirm") {
            auth()
            setBody(request)
        }.checkError().body()
    }

    fun close() {
        client.close()
    }
}

class ApiException(
    val statusCode: Int,
    val error: ApiError
) : Exception(error.error)
