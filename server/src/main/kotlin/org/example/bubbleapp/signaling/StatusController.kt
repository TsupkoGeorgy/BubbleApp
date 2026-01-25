package org.example.bubbleapp.signaling

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class StatusController(
    private val signalingHandler: SignalingHandler
) {

    @GetMapping("/health")
    fun health(): Map<String, Any> = mapOf(
        "status" to "ok",
        "service" to "bubble-signaling"
    )

    @GetMapping("/users/online")
    fun onlineUsers(): Map<String, Any> {
        val usersWithInfo = signalingHandler.getOnlineUsersWithInfo()
        return mapOf(
            "count" to usersWithInfo.size,
            "users" to usersWithInfo
        )
    }

    @GetMapping("/users/{userId}/online")
    fun isUserOnline(@PathVariable userId: String): Map<String, Boolean> = mapOf(
        "online" to signalingHandler.isUserOnline(userId)
    )
}
