package org.example.bubbleapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform