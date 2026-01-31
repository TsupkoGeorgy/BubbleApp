package org.example.bubbleapp.common.dto

import java.time.Instant

data class ErrorResponse(
    val error: String,
    val code: String,
    val details: Map<String, Any>? = null,
    val timestamp: Instant = Instant.now(),
    val path: String? = null
)

data class ValidationErrorResponse(
    val error: String = "Validation failed",
    val code: String = "VALIDATION_ERROR",
    val details: Map<String, List<String>>,
    val timestamp: Instant = Instant.now(),
    val path: String? = null
)
