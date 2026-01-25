package org.example.bubbleapp.call

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.*
import platform.posix.*

// Helper function for network byte order (big-endian)
private fun hostToNetworkShort(value: Int): UShort {
    return (((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)).toUShort()
}

@OptIn(ExperimentalForeignApi::class)
class AudioStreamer {

    private var audioEngine: AVAudioEngine? = null
    private var inputNode: AVAudioInputNode? = null
    private var playerNode: AVAudioPlayerNode? = null

    private var sendSocket: Int = -1
    private var receiveSocket: Int = -1

    private var remoteHost: String? = null
    private var remotePort: Int = 5000
    private var localPort: Int = 5001

    private var isStreaming = false
    private var isMuted = false

    private val bufferSize: UInt = 256u  // ~1KB packets (256 * 4 bytes)

    // Playback format (will be set from hardware)
    private var playbackFormat: AVAudioFormat? = null

    private var receiveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Debug counters
    private var packetsSent = 0
    private var packetsReceived = 0

    var onError: ((String) -> Unit)? = null

    fun start(remoteHost: String, remotePort: Int, localPort: Int) {
        this.remoteHost = remoteHost
        this.remotePort = remotePort
        this.localPort = localPort

        setupAudioSession()
        setupSockets()
        setupAudioEngine()
        startReceiving()

        isStreaming = true
        println("AudioStreamer started: sending to $remoteHost:$remotePort, listening on $localPort")
    }

    fun stop() {
        isStreaming = false
        receiveJob?.cancel()
        receiveJob = null

        inputNode?.removeTapOnBus(0u)

        audioEngine?.stop()
        audioEngine = null
        inputNode = null
        playerNode = null

        if (sendSocket >= 0) {
            close(sendSocket)
            sendSocket = -1
        }
        if (receiveSocket >= 0) {
            close(receiveSocket)
            receiveSocket = -1
        }

        try {
            AVAudioSession.sharedInstance().setActive(false, error = null)
        } catch (e: Exception) {
            println("Error deactivating audio session: ${e.message}")
        }
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

            // Force speaker output
            audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)

            println("Audio session configured, sample rate: ${audioSession.sampleRate}")
            println("Audio output route: ${audioSession.currentRoute.outputs}")
        } catch (e: Exception) {
            println("Error setting up audio session: ${e.message}")
            onError?.invoke("Failed to setup audio: ${e.message}")
        }
    }

    private fun setupSockets() {
        // Create send socket
        sendSocket = socket(AF_INET, SOCK_DGRAM, 0)
        if (sendSocket < 0) {
            println("Failed to create send socket")
            onError?.invoke("Failed to create send socket")
            return
        }

        // Create receive socket
        receiveSocket = socket(AF_INET, SOCK_DGRAM, 0)
        if (receiveSocket < 0) {
            println("Failed to create receive socket")
            onError?.invoke("Failed to create receive socket")
            return
        }

        // Bind receive socket to local port
        memScoped {
            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.toUByte()
            addr.sin_port = hostToNetworkShort(localPort)
            addr.sin_addr.s_addr = INADDR_ANY

            val bindResult = bind(
                receiveSocket,
                addr.ptr.reinterpret(),
                sizeOf<sockaddr_in>().toUInt()
            )
            if (bindResult < 0) {
                println("Failed to bind receive socket to port $localPort")
                onError?.invoke("Failed to bind to port $localPort")
            } else {
                println("Receive socket bound to port $localPort")
            }
        }

        // Set receive socket to non-blocking for timeout handling
        val flags = fcntl(receiveSocket, F_GETFL, 0)
        fcntl(receiveSocket, F_SETFL, flags or O_NONBLOCK)
    }

    private fun setupAudioEngine() {
        audioEngine = AVAudioEngine()
        val engine = audioEngine ?: return

        inputNode = engine.inputNode
        playerNode = AVAudioPlayerNode()
        engine.attachNode(playerNode!!)

        // Use native input format from hardware
        val inputFormat = inputNode?.inputFormatForBus(0u)
        println("Input format: ${inputFormat?.sampleRate} Hz, ${inputFormat?.channelCount} ch")
        println("Input node volume: ${inputNode?.volume}")

        // Create playback format - use same sample rate as input for consistency
        val inputSampleRate = inputFormat?.sampleRate ?: 48000.0
        playbackFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatFloat32,
            sampleRate = inputSampleRate,
            channels = 1u,
            interleaved = false
        )
        println("Playback format: ${playbackFormat?.sampleRate} Hz")

        // Connect player to mixer with playback format
        engine.connect(playerNode!!, to = engine.mainMixerNode, format = playbackFormat)

        // Install tap on input with native format
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
        if (sendSocket < 0) return
        val host = remoteHost ?: return

        val frameLength = buffer.frameLength.toInt()
        if (frameLength == 0) return

        val floatData = buffer.floatChannelData
        if (floatData == null) return

        val channelData = floatData.pointed.value ?: return

        // Log audio level being sent
        if (packetsSent % 500 == 0) {
            var maxSample = 0f
            for (i in 0 until minOf(frameLength, 1000)) {
                val sample = kotlin.math.abs(channelData[i])
                if (sample > maxSample) maxSample = sample
            }
            println("SENDING audio level: max=$maxSample, frames=$frameLength")
        }

        // Max UDP payload size (stay under MTU of 1500)
        val maxSamplesPerPacket = 300  // 300 * 4 = 1200 bytes

        memScoped {
            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.toUByte()
            addr.sin_port = hostToNetworkShort(remotePort)
            addr.sin_addr.s_addr = inet_addr(host)

            var offset = 0
            while (offset < frameLength) {
                val samplesToSend = minOf(maxSamplesPerPacket, frameLength - offset)
                val byteSize = samplesToSend * 4

                val dataPtr = (channelData.toLong() + offset * 4).toCPointer<FloatVar>()

                val sent = sendto(
                    sendSocket,
                    dataPtr,
                    byteSize.toULong(),
                    0,
                    addr.ptr.reinterpret(),
                    sizeOf<sockaddr_in>().toUInt()
                )

                if (sent > 0) {
                    packetsSent++
                    if (packetsSent % 500 == 0) {
                        println("Sent $packetsSent packets")
                    }
                } else {
                    if (packetsSent % 100 == 0) {
                        println("Send failed, errno: ${posix_errno()}")
                    }
                }

                offset += samplesToSend
            }
        }
    }

    private fun startReceiving() {
        receiveJob = scope.launch {
            val bufferSizeBytes = 4096
            val nativeBuffer = nativeHeap.allocArray<UByteVar>(bufferSizeBytes)

            try {
                memScoped {
                    val addr = alloc<sockaddr_in>()
                    val addrLen = alloc<socklen_tVar>()

                    while (isActive && isStreaming) {
                        addrLen.value = sizeOf<sockaddr_in>().toUInt()

                        val received = recvfrom(
                            receiveSocket,
                            nativeBuffer,
                            bufferSizeBytes.toULong(),
                            0,
                            addr.ptr.reinterpret(),
                            addrLen.ptr
                        )

                        if (received > 0) {
                            packetsReceived++
                            if (packetsReceived % 100 == 0) {
                                println("Received $packetsReceived packets, last size: $received bytes")
                            }
                            // Copy data to ByteArray
                            val dataArray = ByteArray(received.toInt()) { i -> nativeBuffer[i].toByte() }
                            dispatch_async(dispatch_get_main_queue()) {
                                playReceivedAudio(dataArray)
                            }
                        } else {
                            // No data available, wait a bit
                            delay(5)
                        }
                    }
                }
            } finally {
                nativeHeap.free(nativeBuffer)
            }
        }
    }

    private var buffersPlayed = 0

    private fun playReceivedAudio(data: ByteArray) {
        val player = playerNode ?: run {
            println("Player is null!")
            return
        }
        val engine = audioEngine ?: run {
            println("Engine is null!")
            return
        }
        val format = playbackFormat ?: run {
            println("Format is null!")
            return
        }

        if (!engine.isRunning()) {
            println("Engine not running!")
            return
        }

        if (!player.isPlaying()) {
            println("Player not playing, starting...")
            player.play()
        }

        val sampleCount = data.size / 4 // Float32 = 4 bytes
        if (sampleCount == 0) return

        val frameCount = sampleCount.toUInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount) ?: run {
            println("Failed to create buffer!")
            return
        }
        buffer.frameLength = frameCount

        val destPtr = buffer.floatChannelData?.pointed?.value ?: run {
            println("Failed to get buffer pointer!")
            return
        }

        // Copy Float32 data
        data.usePinned { pinned ->
            memcpy(destPtr, pinned.addressOf(0), data.size.toULong())
        }

        // Check audio level
        if (buffersPlayed % 100 == 0) {
            var maxSample = 0f
            for (i in 0 until sampleCount) {
                val sample = kotlin.math.abs(destPtr[i])
                if (sample > maxSample) maxSample = sample
            }
            println("Audio level: max=$maxSample (should be 0.0-1.0)")
        }

        player.scheduleBuffer(buffer, completionHandler = null)

        buffersPlayed++
        if (buffersPlayed % 100 == 0) {
            println("Played $buffersPlayed buffers, player.isPlaying=${player.isPlaying()}")
        }
    }
}
