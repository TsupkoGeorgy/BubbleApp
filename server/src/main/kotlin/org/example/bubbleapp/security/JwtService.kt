package org.example.bubbleapp.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}")
    private val jwtSecret: String,

    @Value("\${jwt.access-token-expiration}")
    private val accessTokenExpiration: Long,

    @Value("\${jwt.refresh-token-expiration}")
    private val refreshTokenExpiration: Long
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    fun generateAccessToken(userId: UUID, phone: String, username: String?): String {
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpiration)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("phone", phone)
            .claim("username", username)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    fun generateRefreshToken(): String {
        return UUID.randomUUID().toString()
    }

    fun getRefreshTokenExpirationMs(): Long = refreshTokenExpiration

    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaimsJws(token)
            !claims.expiration.before(Date())
        } catch (e: ExpiredJwtException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun getUserIdFromToken(token: String): UUID {
        val claims = parseClaimsJws(token)
        return UUID.fromString(claims.subject)
    }

    fun getPhoneFromToken(token: String): String {
        val claims = parseClaimsJws(token)
        return claims["phone"] as String
    }

    fun getUsernameFromToken(token: String): String? {
        val claims = parseClaimsJws(token)
        return claims["username"] as String?
    }

    fun getUserPrincipalFromToken(token: String): UserPrincipal {
        val claims = parseClaimsJws(token)
        return UserPrincipal(
            userId = UUID.fromString(claims.subject),
            phone = claims["phone"] as String,
            userName = claims["username"] as String?
        )
    }

    private fun parseClaimsJws(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
