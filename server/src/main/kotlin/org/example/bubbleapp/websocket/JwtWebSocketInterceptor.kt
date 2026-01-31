package org.example.bubbleapp.websocket

import org.example.bubbleapp.security.JwtService
import org.example.bubbleapp.security.UserPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

@Component
class JwtWebSocketInterceptor(
    private val jwtService: JwtService
) : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(JwtWebSocketInterceptor::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (request is ServletServerHttpRequest) {
            val token = extractToken(request)

            if (token != null && jwtService.validateToken(token)) {
                val userPrincipal = jwtService.getUserPrincipalFromToken(token)
                attributes["user"] = userPrincipal
                attributes["userId"] = userPrincipal.userId.toString()
                log.debug("WebSocket handshake authenticated for user: ${userPrincipal.userId}")
                return true
            }

            // Allow unauthenticated connections for backward compatibility with signaling
            // The signaling handler can check for authentication if needed
            log.debug("WebSocket handshake without authentication")
        }
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        // No-op
    }

    private fun extractToken(request: ServletServerHttpRequest): String? {
        // Try Authorization header first
        val authHeader = request.servletRequest.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        // Try query parameter
        val token = request.servletRequest.getParameter("token")
        if (token != null) {
            return token
        }

        return null
    }
}
