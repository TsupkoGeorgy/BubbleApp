package org.example.bubbleapp.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.example.bubbleapp.auth.service.InvalidCodeException
import org.example.bubbleapp.auth.service.InvalidTokenException
import org.example.bubbleapp.common.dto.ErrorResponse
import org.example.bubbleapp.common.dto.ValidationErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ValidationErrorResponse> {
        val errors = ex.bindingResult.fieldErrors
            .groupBy { it.field }
            .mapValues { entry -> entry.value.mapNotNull { it.defaultMessage } }

        log.warn("Validation error: $errors")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ValidationErrorResponse(
                    details = errors,
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(InvalidCodeException::class)
    fun handleInvalidCode(
        ex: InvalidCodeException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Invalid verification code: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = ex.message ?: "Invalid verification code",
                    code = "INVALID_CODE",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(
        ex: InvalidTokenException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Invalid token: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    error = ex.message ?: "Invalid token",
                    code = "INVALID_TOKEN",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Authentication error: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    error = "Authentication required",
                    code = "UNAUTHORIZED",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Access denied: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    error = "Access denied",
                    code = "FORBIDDEN",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(
        ex: EntityNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Entity not found: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    error = ex.message ?: "Resource not found",
                    code = "NOT_FOUND",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = "An unexpected error occurred",
                    code = "INTERNAL_ERROR",
                    path = request.requestURI
                )
            )
    }
}

class EntityNotFoundException(message: String) : RuntimeException(message)
