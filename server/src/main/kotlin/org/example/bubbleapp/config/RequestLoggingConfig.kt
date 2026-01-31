package org.example.bubbleapp.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.bubbleapp.security.UserPrincipal
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Configuration
class RequestLoggingConfig {

    @Bean
    fun requestLoggingFilter(): RequestLoggingFilter {
        return RequestLoggingFilter()
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-ID"
        const val CORRELATION_ID_MDC = "correlationId"
        const val USER_ID_MDC = "userId"
        const val REQUEST_URI_MDC = "requestUri"

        // Endpoints to skip detailed logging
        private val SKIP_LOGGING = setOf(
            "/status",
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs",
            "/favicon.ico"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.currentTimeMillis()

        // Get or generate correlation ID
        val correlationId = request.getHeader(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString().substring(0, 8)

        // Set MDC context
        MDC.put(CORRELATION_ID_MDC, correlationId)
        MDC.put(REQUEST_URI_MDC, request.requestURI)

        // Add correlation ID to response
        response.setHeader(CORRELATION_ID_HEADER, correlationId)

        try {
            // Log request (skip for noisy endpoints)
            if (shouldLog(request)) {
                logRequest(request)
            }

            filterChain.doFilter(request, response)

            // Add user ID to MDC after security filter runs
            val userId = getUserId()
            if (userId != null) {
                MDC.put(USER_ID_MDC, userId)
            }

            // Log response
            if (shouldLog(request)) {
                val duration = System.currentTimeMillis() - startTime
                logResponse(request, response, duration)
            }
        } finally {
            MDC.clear()
        }
    }

    private fun shouldLog(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return SKIP_LOGGING.none { uri.startsWith(it) }
    }

    private fun logRequest(request: HttpServletRequest) {
        val method = request.method
        val uri = request.requestURI
        val query = request.queryString?.let { "?$it" } ?: ""
        val clientIp = getClientIp(request)

        log.info("→ $method $uri$query [IP: $clientIp]")
    }

    private fun logResponse(request: HttpServletRequest, response: HttpServletResponse, durationMs: Long) {
        val method = request.method
        val uri = request.requestURI
        val status = response.status

        val logLevel = when {
            status >= 500 -> "error"
            status >= 400 -> "warn"
            else -> "info"
        }

        val message = "← $method $uri → $status (${durationMs}ms)"

        when (logLevel) {
            "error" -> log.error(message)
            "warn" -> log.warn(message)
            else -> log.info(message)
        }

        // Log slow requests
        if (durationMs > 1000) {
            log.warn("Slow request: $method $uri took ${durationMs}ms")
        }
    }

    private fun getUserId(): String? {
        return try {
            val auth = SecurityContextHolder.getContext().authentication
            (auth?.principal as? UserPrincipal)?.userId?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (xForwardedFor != null) {
            xForwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }
}
