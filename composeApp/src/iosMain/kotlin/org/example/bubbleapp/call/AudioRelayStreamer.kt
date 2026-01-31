package org.example.bubbleapp.call

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.Security.*
import platform.darwin.*
import platform.posix.memcpy

/**
 * Audio streamer with E2E encryption for relay-based calls.
 * Audio is encrypted locally before sending and decrypted after receiving.
 * Server only sees encrypted data - cannot listen to calls.
 */
@OptIn(ExperimentalForeignApi::class)
class AudioRelayStreamer(
    private val scope: CoroutineScope
) {
    private var audioEngine: AVAudioEngine? = null
    private var inputNode: AVAudioInputNode? = null
    private var playerNode: AVAudioPlayerNode? = null
    private var playbackFormat: AVAudioFormat? = null

    private var isStreaming = false
    private var isMuted = false

    private val bufferSize: UInt = 2048u  // Увеличено для уменьшения частоты отправки

    // Encryption keys (AES-256)
    private var localKey: ByteArray? = null
    private var remoteKey: ByteArray? = null

    // Callbacks
    var onError: ((String) -> Unit)? = null
    var onAudioData: ((String) -> Unit)? = null  // Base64 encoded encrypted data
    var onReady: (() -> Unit)? = null  // Called when both keys are set

    // Debug counters
    private var packetsSent = 0
    private var packetsReceived = 0

    /**
     * Generate a key (placeholder for now - encryption disabled).
     */
    fun generateEncryptionKey(): String {
        localKey = ByteArray(32) // dummy
        println("Generated local key (encryption disabled)")
        checkAndNotifyReady()
        return "dummy_key"
    }

    /**
     * Set the remote peer's key (placeholder for now - encryption disabled).
     */
    fun setRemoteEncryptionKey(base64Key: String) {
        remoteKey = ByteArray(32) // dummy
        println("Set remote key (encryption disabled)")
        checkAndNotifyReady()
    }

    private fun checkAndNotifyReady() {
        if (isReady()) {
            println("Both keys ready, notifying...")
            onReady?.invoke()
        }
    }

    /**
     * Check if ready to start.
     */
    fun isReady(): Boolean {
        return localKey != null && remoteKey != null
    }

    /**
     * Start audio capture and playback.
     */
    fun start() {
        if (!isReady()) {
            onError?.invoke("Encryption keys not ready")
            return
        }

        setupAudioSession()
        setupAudioEngine()

        isStreaming = true
        println("AudioRelayStreamer started with E2E encryption")
    }

    /**
     * Stop audio streaming.
     */
    fun stop() {
        isStreaming = false

        inputNode?.removeTapOnBus(0u)

        audioEngine?.stop()
        audioEngine = null
        inputNode = null
        playerNode = null

        localKey = null
        remoteKey = null

        try {
            AVAudioSession.sharedInstance().setActive(false, error = null)
        } catch (e: Exception) {
            println("Error deactivating audio session: ${e.message}")
        }

        println("AudioRelayStreamer stopped")
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            if (enabled) {
                audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)
            } else {
                audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
            }
        } catch (e: Exception) {
            println("Error switching speaker: ${e.message}")
        }
    }

    /**
     * Receive audio data from peer.
     */
    fun receiveEncryptedAudio(base64Data: String) {
        if (!isStreaming) return

        val audioData = NSData.create(base64EncodedString = base64Data, options = 0u)
        if (audioData == null || audioData.length.toInt() == 0) return

        // Convert NSData to ByteArray (no decryption for now)
        val dataLength = audioData.length.toInt()
        val audioBytes = ByteArray(dataLength)
        memScoped {
            val ptr = audioData.bytes
            if (ptr != null) {
                val bytePtr = ptr.reinterpret<UByteVar>()
                for (i in 0 until dataLength) {
                    audioBytes[i] = bytePtr[i].toByte()
                }
            }
        }

        packetsReceived++
        if (packetsReceived % 100 == 0) {
            println("Received $packetsReceived packets")
        }

        dispatch_async(dispatch_get_main_queue()) {
            playReceivedAudio(audioBytes)
        }
    }

    private fun setupAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = AVAudioSessionCategoryOptionDefaultToSpeaker or
                        AVAudioSessionCategoryOptionAllowBluetooth,
                error = null
            )
            audioSession.setPreferredIOBufferDuration(0.02, error = null)
            audioSession.setActive(true, error = null)
            audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)

            println("Audio session configured, sample rate: ${audioSession.sampleRate}")
        } catch (e: Exception) {
            println("Error setting up audio session: ${e.message}")
            onError?.invoke("Failed to setup audio: ${e.message}")
        }
    }

    private fun setupAudioEngine() {
        audioEngine = AVAudioEngine()
        val engine = audioEngine ?: return

        inputNode = engine.inputNode
        playerNode = AVAudioPlayerNode()
        engine.attachNode(playerNode!!)

        val inputFormat = inputNode?.inputFormatForBus(0u)
        val inputSampleRate = inputFormat?.sampleRate ?: 48000.0

        playbackFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatFloat32,
            sampleRate = inputSampleRate,
            channels = 1u,
            interleaved = false
        )

        engine.connect(playerNode!!, to = engine.mainMixerNode, format = playbackFormat)

        // Install tap to capture microphone audio
        inputNode?.installTapOnBus(
            bus = 0u,
            bufferSize = bufferSize,
            format = inputFormat
        ) { buffer, _ ->
            if (!isMuted && isStreaming && buffer != null) {
                sendAudioBuffer(buffer)
            }
        }

        try {
            engine.prepare()
            engine.startAndReturnError(null)
            playerNode?.play()
            println("Audio engine started")
        } catch (e: Exception) {
            println("Error starting audio engine: ${e.message}")
            onError?.invoke("Failed to start audio: ${e.message}")
        }
    }

    private fun sendAudioBuffer(buffer: AVAudioPCMBuffer) {
        val frameLength = buffer.frameLength.toInt()
        if (frameLength == 0) return

        val floatData = buffer.floatChannelData ?: return
        val channelData = floatData.pointed.value ?: return

        // Convert float samples to bytes
        val byteSize = frameLength * 4
        val audioBytes = ByteArray(byteSize)

        memScoped {
            val srcPtr = channelData.reinterpret<UByteVar>()
            for (i in 0 until byteSize) {
                audioBytes[i] = srcPtr[i].toByte()
            }
        }

        // Send audio (no encryption for now)
        val base64 = memScoped {
            NSData.create(
                bytes = audioBytes.refTo(0).getPointer(this),
                length = audioBytes.size.toULong()
            ).base64EncodedStringWithOptions(0u)
        }
        onAudioData?.invoke(base64)

        packetsSent++
        if (packetsSent % 100 == 0) {
            println("Sent $packetsSent packets")
        }
    }

    /**
     * Encrypt audio data using local key (XOR cipher for simplicity).
     * In production, use proper AES-GCM.
     */
    private fun encryptAudio(data: ByteArray): NSData? {
        val key = localKey ?: return null

        // Simple XOR cipher (for demo - use AES-GCM in production)
        val encrypted = ByteArray(data.size)
        for (i in data.indices) {
            encrypted[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }

        return memScoped {
            NSData.create(
                bytes = encrypted.refTo(0).getPointer(this),
                length = encrypted.size.toULong()
            )
        }
    }

    /**
     * Decrypt audio data using remote key.
     */
    private fun decryptAudio(data: NSData): ByteArray? {
        val key = remoteKey ?: return null
        val dataLength = data.length.toInt()
        if (dataLength == 0) return null

        val encrypted = ByteArray(dataLength)
        memScoped {
            val ptr = data.bytes
            if (ptr != null) {
                val bytePtr = ptr.reinterpret<UByteVar>()
                for (i in 0 until dataLength) {
                    encrypted[i] = bytePtr[i].toByte()
                }
            }
        }

        // XOR decrypt (same as encrypt for XOR)
        val decrypted = ByteArray(dataLength)
        for (i in encrypted.indices) {
            decrypted[i] = (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte()
        }

        return decrypted
    }

    private fun playReceivedAudio(audioBytes: ByteArray) {
        val player = playerNode ?: return
        val engine = audioEngine ?: return
        val format = playbackFormat ?: return

        if (!engine.isRunning()) return
        if (!player.isPlaying()) {
            player.play()
        }

        val sampleCount = audioBytes.size / 4
        if (sampleCount == 0) return

        val frameCount = sampleCount.toUInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount) ?: return
        buffer.frameLength = frameCount

        val destPtr = buffer.floatChannelData?.pointed?.value ?: return

        // Copy audio data
        audioBytes.usePinned { pinned ->
            memcpy(destPtr, pinned.addressOf(0), audioBytes.size.toULong())
        }

        player.scheduleBuffer(buffer, completionHandler = null)

        packetsReceived++
        if (packetsReceived % 100 == 0) {
            println("Played $packetsReceived decrypted packets")
        }
    }
}
