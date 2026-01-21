package org.example.bubbleapp.video

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferIsValid
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import kotlin.concurrent.Volatile

data class VideoEncodingConfig(
    val width: Int = 720,
    val height: Int = 1280,
    val codec: String? = AVVideoCodecTypeH264
)

data class AudioEncodingConfig(
    val sampleRate: Double = 44100.0,
    val channels: Int = 1,
    val bitRate: Int = 64000
)

sealed class EncodingState {
    object Idle : EncodingState()
    object Recording : EncodingState()
    object Finishing : EncodingState()
    data class Error(val message: String) : EncodingState()
}

@OptIn(ExperimentalForeignApi::class)
interface MediaEncodingManager {
    val state: EncodingState
    val writerQueue: dispatch_queue_t

    fun startEncoding(outputUrl: NSURL): Boolean
    fun appendVideoSample(sampleBuffer: CMSampleBufferRef): Boolean
    fun appendAudioSample(sampleBuffer: CMSampleBufferRef): Boolean
    fun finishEncoding(completion: (success: Boolean, error: String?) -> Unit)
    fun cancelEncoding()
}

@OptIn(ExperimentalForeignApi::class)
class IOSMediaEncodingManager(
    private val videoConfig: VideoEncodingConfig = VideoEncodingConfig(),
    private val audioConfig: AudioEncodingConfig = AudioEncodingConfig()
) : MediaEncodingManager {

    private var assetWriter: AVAssetWriter? = null
    private var videoWriterInput: AVAssetWriterInput? = null
    private var audioWriterInput: AVAssetWriterInput? = null

    @Volatile
    private var _state: EncodingState = EncodingState.Idle
    override val state: EncodingState get() = _state

    @Volatile
    private var sessionStarted = false

    override val writerQueue: dispatch_queue_t =
        dispatch_queue_create("com.bubbleapp.encoding", null)

    override fun startEncoding(outputUrl: NSURL): Boolean {
        if (_state != EncodingState.Idle) {
            println("MediaEncodingManager: Cannot start encoding, current state: $_state")
            return false
        }

        val writer = AVAssetWriter.assetWriterWithURL(outputUrl, AVFileTypeMPEG4, null)
        if (writer == null) {
            _state = EncodingState.Error("Failed to create AVAssetWriter")
            return false
        }

        // Video input
        val videoSettings = mapOf<Any?, Any?>(
            AVVideoCodecKey to videoConfig.codec,
            AVVideoWidthKey to videoConfig.width,
            AVVideoHeightKey to videoConfig.height
        )

        val videoInput = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = videoSettings
        ).apply {
            expectsMediaDataInRealTime = true
        }

        if (!writer.canAddInput(videoInput)) {
            _state = EncodingState.Error("Cannot add video input to writer")
            return false
        }
        writer.addInput(videoInput)

        // Audio input
        val audioSettings = mapOf<Any?, Any?>(
            "AVFormatIDKey" to 1633772320L, // kAudioFormatMPEG4AAC
            "AVSampleRateKey" to audioConfig.sampleRate,
            "AVNumberOfChannelsKey" to audioConfig.channels,
            "AVEncoderBitRateKey" to audioConfig.bitRate
        )

        val audioInput = AVAssetWriterInput(
            mediaType = AVMediaTypeAudio,
            outputSettings = audioSettings
        ).apply {
            expectsMediaDataInRealTime = true
        }

        if (writer.canAddInput(audioInput)) {
            writer.addInput(audioInput)
            audioWriterInput = audioInput
        } else {
            println("MediaEncodingManager: Cannot add audio input, continuing without audio")
            audioWriterInput = null
        }

        assetWriter = writer
        videoWriterInput = videoInput
        sessionStarted = false
        _state = EncodingState.Recording

        println("MediaEncodingManager: Encoding started for $outputUrl")
        return true
    }

    override fun appendVideoSample(sampleBuffer: CMSampleBufferRef): Boolean {
        if (_state != EncodingState.Recording) return false
        if (!CMSampleBufferIsValid(sampleBuffer)) return false

        val writer = assetWriter ?: return false
        val writerInput = videoWriterInput ?: return false

        // Start session on first video frame
        if (!sessionStarted && writer.status == AVAssetWriterStatusUnknown) {
            val timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            if (writer.startWriting()) {
                writer.startSessionAtSourceTime(timestamp)
                sessionStarted = true
                println("MediaEncodingManager: Recording session started")
            } else {
                _state = EncodingState.Error("Failed to start writing: ${writer.error}")
                return false
            }
        }

        if (writer.status == AVAssetWriterStatusWriting && writerInput.readyForMoreMediaData) {
            if (!writerInput.appendSampleBuffer(sampleBuffer)) {
                println("MediaEncodingManager: Failed to append video buffer: ${writer.error}")
                return false
            }
            return true
        }

        return false
    }

    override fun appendAudioSample(sampleBuffer: CMSampleBufferRef): Boolean {
        if (_state != EncodingState.Recording || !sessionStarted) return false
        if (!CMSampleBufferIsValid(sampleBuffer)) return false

        val writer = assetWriter ?: return false
        val writerInput = audioWriterInput ?: return false

        if (writer.status == AVAssetWriterStatusWriting && writerInput.readyForMoreMediaData) {
            if (!writerInput.appendSampleBuffer(sampleBuffer)) {
                println("MediaEncodingManager: Failed to append audio buffer: ${writer.error}")
                return false
            }
            return true
        }

        return false
    }

    override fun finishEncoding(completion: (success: Boolean, error: String?) -> Unit) {
        if (_state != EncodingState.Recording) {
            completion(false, "Not recording")
            return
        }

        _state = EncodingState.Finishing
        val writer = assetWriter

        if (writer == null) {
            _state = EncodingState.Idle
            completion(false, "No active writer")
            return
        }

        dispatch_async(writerQueue) {
            videoWriterInput?.markAsFinished()
            audioWriterInput?.markAsFinished()

            writer.finishWritingWithCompletionHandler {
                dispatch_async(dispatch_get_main_queue()) {
                    val success = writer.status == AVAssetWriterStatusCompleted
                    val error = if (!success) writer.error?.localizedDescription else null

                    cleanup()
                    _state = EncodingState.Idle

                    println("MediaEncodingManager: Encoding finished, success=$success")
                    completion(success, error)
                }
            }
        }
    }

    override fun cancelEncoding() {
        assetWriter?.cancelWriting()
        cleanup()
        _state = EncodingState.Idle
        println("MediaEncodingManager: Encoding cancelled")
    }

    private fun cleanup() {
        assetWriter = null
        videoWriterInput = null
        audioWriterInput = null
        sessionStarted = false
    }
}
