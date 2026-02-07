package org.example.bubbleapp.util

import org.example.bubbleapp.AppState

/**
 * Fixes URLs that incorrectly point to localhost by replacing with the actual server host.
 * This is needed because the backend may generate URLs with localhost for S3/MinIO storage.
 */
fun fixAvatarUrl(url: String?): String? {
    if (url == null) return null

    // Extract host from AppState.DEFAULT_BASE_URL
    val baseUrl = AppState.DEFAULT_BASE_URL
    val serverHost = baseUrl
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore(":")
        .substringBefore("/")

    // Replace localhost with actual server host
    return url
        .replace("localhost", serverHost)
        .replace("127.0.0.1", serverHost)
}
