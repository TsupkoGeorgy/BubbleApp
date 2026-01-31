package org.example.bubbleapp.data.auth

import platform.Foundation.*

actual class TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun saveTokens(tokens: AuthTokens) {
        defaults.setObject(tokens.accessToken, forKey = "access_token")
        defaults.setObject(tokens.refreshToken, forKey = "refresh_token")
        defaults.setDouble(tokens.expiresAt.toDouble(), forKey = "expires_at")
        defaults.setObject(tokens.userId, forKey = "user_id")
        defaults.synchronize()
    }

    actual suspend fun getTokens(): AuthTokens? {
        val accessToken = defaults.stringForKey("access_token") ?: return null
        val refreshToken = defaults.stringForKey("refresh_token") ?: return null
        val userId = defaults.stringForKey("user_id") ?: return null

        // Check if expiresAt exists
        if (defaults.objectForKey("expires_at") == null) return null

        val expiresAt = defaults.doubleForKey("expires_at").toLong()

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            userId = userId
        )
    }

    actual suspend fun clearTokens() {
        defaults.removeObjectForKey("access_token")
        defaults.removeObjectForKey("refresh_token")
        defaults.removeObjectForKey("expires_at")
        defaults.removeObjectForKey("user_id")
        defaults.synchronize()
    }
}

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}
