package org.example.bubbleapp.notification

import org.example.bubbleapp.device.entity.Device
import org.example.bubbleapp.device.entity.Platform
import org.example.bubbleapp.device.service.DeviceService
import org.example.bubbleapp.message.dto.models.MessageResponse
import org.example.bubbleapp.websocket.chat.ChatWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PushNotificationService(
    private val deviceService: DeviceService,
    private val chatWebSocketHandler: ChatWebSocketHandler,
    @Value("\${push.fcm.enabled:false}")
    private val fcmEnabled: Boolean,
    @Value("\${push.apns.enabled:false}")
    private val apnsEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(PushNotificationService::class.java)

    fun sendNewMessageNotification(
        recipientUserId: UUID,
        chatId: UUID,
        chatName: String?,
        message: MessageResponse
    ) {
        // Don't send push if user is online (they get real-time via WebSocket)
        if (chatWebSocketHandler.isUserOnline(recipientUserId)) {
            log.debug("User $recipientUserId is online, skipping push notification")
            return
        }

        val devices = deviceService.getDevicesWithPushToken(recipientUserId)
        if (devices.isEmpty()) {
            log.debug("No devices with push token for user $recipientUserId")
            return
        }

        val senderName = message.sender.displayName
            ?: message.sender.username
            ?: message.sender.phone

        val title = chatName ?: senderName
        val body = when (message.type.name) {
            "TEXT" -> message.content ?: "New message"
            "VIDEO" -> "$senderName sent a video"
            "IMAGE" -> "$senderName sent an image"
            "VOICE" -> "$senderName sent a voice message"
            "FILE" -> "$senderName sent a file"
            else -> "New message"
        }

        val data = mapOf(
            "type" to "NEW_MESSAGE",
            "chatId" to chatId.toString(),
            "messageId" to message.id.toString(),
            "senderId" to message.sender.id.toString()
        )

        devices.forEach { device ->
            sendPushToDevice(device, title, body, data)
        }
    }

    fun sendCallNotification(
        recipientUserId: UUID,
        callerId: UUID,
        callerName: String,
        callType: String // "video" or "audio"
    ) {
        val devices = deviceService.getDevicesWithPushToken(recipientUserId)
        if (devices.isEmpty()) {
            log.debug("No devices with push token for user $recipientUserId")
            return
        }

        val title = "Incoming ${if (callType == "video") "video" else ""} call"
        val body = "$callerName is calling..."

        val data = mapOf(
            "type" to "INCOMING_CALL",
            "callerId" to callerId.toString(),
            "callerName" to callerName,
            "callType" to callType
        )

        devices.forEach { device ->
            sendPushToDevice(device, title, body, data, isVoip = true)
        }
    }

    private fun sendPushToDevice(
        device: Device,
        title: String,
        body: String,
        data: Map<String, String>,
        isVoip: Boolean = false
    ) {
        val pushToken = device.pushToken ?: return

        when (device.platform) {
            Platform.IOS -> sendApns(pushToken, title, body, data, isVoip)
            Platform.ANDROID -> sendFcm(pushToken, title, body, data)
            Platform.WEB -> sendFcm(pushToken, title, body, data) // Web uses FCM
        }
    }

    private fun sendFcm(token: String, title: String, body: String, data: Map<String, String>) {
        if (!fcmEnabled) {
            log.info("[FCM MOCK] To: $token, Title: $title, Body: $body, Data: $data")
            return
        }

        // TODO: Implement real FCM sending
        // Using Firebase Admin SDK:
        // val message = Message.builder()
        //     .setToken(token)
        //     .setNotification(Notification.builder().setTitle(title).setBody(body).build())
        //     .putAllData(data)
        //     .build()
        // FirebaseMessaging.getInstance().send(message)

        log.info("[FCM] Sent to $token: $title")
    }

    private fun sendApns(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>,
        isVoip: Boolean
    ) {
        if (!apnsEnabled) {
            log.info("[APNs MOCK] To: $token, Title: $title, Body: $body, VoIP: $isVoip, Data: $data")
            return
        }

        // TODO: Implement real APNs sending
        // Using pushy library or AWS SNS:
        // val payload = ApnsPayloadBuilder()
        //     .setAlertTitle(title)
        //     .setAlertBody(body)
        //     .addCustomProperty("data", data)
        //     .build()
        // apnsClient.sendNotification(SimpleApnsPushNotification(token, bundleId, payload))

        log.info("[APNs] Sent to $token: $title (VoIP: $isVoip)")
    }
}
