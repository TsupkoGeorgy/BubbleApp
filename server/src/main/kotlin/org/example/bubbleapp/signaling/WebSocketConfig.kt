package org.example.bubbleapp.signaling

import org.example.bubbleapp.websocket.JwtWebSocketInterceptor
import org.example.bubbleapp.websocket.chat.ChatWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val signalingHandler: SignalingHandler,
    private val chatWebSocketHandler: ChatWebSocketHandler,
    private val jwtWebSocketInterceptor: JwtWebSocketInterceptor
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // Signaling for calls
        registry
            .addHandler(signalingHandler, "/call")
            .addInterceptors(jwtWebSocketInterceptor)
            .setAllowedOrigins("*")

        // Real-time chat
        registry
            .addHandler(chatWebSocketHandler, "/ws/chat")
            .addInterceptors(jwtWebSocketInterceptor)
            .setAllowedOrigins("*")
    }

    @Bean
    fun createWebSocketContainer(): ServletServerContainerFactoryBean {
        val container = ServletServerContainerFactoryBean()
        container.setMaxTextMessageBufferSize(512 * 1024)  // 512KB
        container.setMaxBinaryMessageBufferSize(512 * 1024)
        container.setMaxSessionIdleTimeout(60000)  // 60 seconds
        return container
    }
}
