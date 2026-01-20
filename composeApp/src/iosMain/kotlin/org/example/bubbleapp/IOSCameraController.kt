package org.example.bubbleapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferIsValid
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.UIKit.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import kotlin.concurrent.Volatile

@Composable
actual fun rememberCameraController(): CameraController {
    val controller = remember { IOSCameraController() }
    return controller
}

@OptIn(ExperimentalForeignApi::class)
class IOSCameraController : CameraController {

    private var captureVideoInput: AVCaptureDeviceInput? = null
    private var captureAudioInput: AVCaptureDeviceInput? = null
    private var currentCameraPosition = AVCaptureDevicePositionBack

    // AVAssetWriter for manual frame writing
    private var assetWriter: AVAssetWriter? = null
    private var videoWriterInput: AVAssetWriterInput? = null
    private var audioWriterInput: AVAssetWriterInput? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var sessionStarted = false

    private var currentVideoUrl: NSURL? = null

    // Queue for capture session operations (must not be main thread)
    private val sessionQueue: dispatch_queue_t = dispatch_queue_create("com.bubbleapp.session", null)

    // Single queue for all writing operations to avoid race conditions
    private val writerQueue: dispatch_queue_t = dispatch_queue_create("com.bubbleapp.writer", null)

    // Data outputs for capturing frames
    private val videoDataOutput = AVCaptureVideoDataOutput().apply {
        alwaysDiscardsLateVideoFrames = true
        videoSettings = mapOf<Any?, Any?>(
            kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA
        )
    }

    private val audioDataOutput = AVCaptureAudioDataOutput()

    private fun getCameraDevice(
        position: AVCaptureDevicePosition
    ): AVCaptureDevice? {
        val discovery = AVCaptureDeviceDiscoverySession
            .discoverySessionWithDeviceTypes(
                deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
                mediaType = AVMediaTypeVideo,
                position = position
            )

        return discovery.devices.firstOrNull() as? AVCaptureDevice
    }

    override fun createInlinePlayer(fileName: String): InlineVideoViewController {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() ?: error("Documents dir not found")

        val url = NSURL.fileURLWithPath("$documentsDir/$fileName")
        return InlineVideoViewController(url)
    }

    private val captureSession = AVCaptureSession().apply {
        sessionPreset = AVCaptureSessionPreset1280x720
    }

    val previewLayer = AVCaptureVideoPreviewLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    override var onVideoRecorded: ((fileName: String, fileSize: Long) -> Unit)? = null

    override var onCameraReady: (() -> Unit)? = null

    private var frameCount = 0

    // Delegate for video frames
    private val videoOutputDelegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection
        ) {
            // Log every 30th frame to avoid spam
            frameCount++
            if (frameCount % 30 == 0) {
                println("Video frame #$frameCount, isRecording=$isRecording, sessionStarted=$sessionStarted")
            }

            if (!isRecording) return

            val sampleBuffer = didOutputSampleBuffer ?: return
            if (!CMSampleBufferIsValid(sampleBuffer)) return

            val writer = assetWriter ?: run {
                println("Writer is null!")
                return
            }
            val writerInput = videoWriterInput ?: run {
                println("Video writer input is null!")
                return
            }

            // Start session on first video frame
            if (!sessionStarted && writer.status == AVAssetWriterStatusUnknown) {
                val timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
                println("Starting writer session...")
                if (writer.startWriting()) {
                    writer.startSessionAtSourceTime(timestamp)
                    sessionStarted = true
                    println("Recording session started successfully")
                } else {
                    println("Failed to start writing: ${writer.error}")
                    return
                }
            }

            if (writer.status == AVAssetWriterStatusWriting && writerInput.readyForMoreMediaData) {
                if (!writerInput.appendSampleBuffer(sampleBuffer)) {
                    println("Failed to append video buffer: ${writer.error}")
                }
            } else if (writer.status != AVAssetWriterStatusWriting) {
                println("Writer not in writing state: ${writer.status}")
            }
        }
    }

    // Delegate for audio samples
    private val audioOutputDelegate = object : NSObject(), AVCaptureAudioDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection
        ) {
            if (!isRecording || !sessionStarted) return

            val sampleBuffer = didOutputSampleBuffer ?: return
            if (!CMSampleBufferIsValid(sampleBuffer)) return

            val writer = assetWriter ?: return
            val writerInput = audioWriterInput ?: return

            if (writer.status == AVAssetWriterStatusWriting && writerInput.readyForMoreMediaData) {
                if (!writerInput.appendSampleBuffer(sampleBuffer)) {
                    println("Failed to append audio buffer: ${writer.error}")
                }
            }
        }
    }

    override fun playVideo(fileName: String) {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() ?: return

        val path = "$documentsDir/$fileName"
        val url = NSURL.fileURLWithPath(path)

        val player = AVPlayer(uRL = url)
        val playerVC = AVPlayerViewController().apply {
            this.player = player
        }

        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: return

        rootVC.presentViewController(playerVC, animated = true) {
            player.play()
        }
    }

    private fun generateVideoFileUrl(): NSURL {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() ?: NSTemporaryDirectory()

        val path = "$documentsDir/video_${NSDate().timeIntervalSince1970}.mp4"
        return NSURL.fileURLWithPath(path)
    }

    private fun getFileSize(url: NSURL): Long {
        val attributes = NSFileManager.defaultManager
            .attributesOfItemAtPath(url.path!!, null)

        return (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
    }

    private fun setupAssetWriter(url: NSURL): Boolean {
        val writer = AVAssetWriter.assetWriterWithURL(url, AVFileTypeMPEG4, null)
            ?: return false

        // Video: 720x1280 portrait (swapped from 1280x720 landscape preset)
        val videoSettings = mapOf<Any?, Any?>(
            AVVideoCodecKey to AVVideoCodecTypeH264,
            AVVideoWidthKey to 720,
            AVVideoHeightKey to 1280
        )

        val videoInput = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = videoSettings
        ).apply {
            expectsMediaDataInRealTime = true
        }

        if (writer.canAddInput(videoInput)) {
            writer.addInput(videoInput)
        } else {
            println("Cannot add video input")
            return false
        }

        // Audio - try to add with explicit settings using string keys
        // kAudioFormatMPEG4AAC = 1633772320 ('aac ')
        val audioSettings = mapOf<Any?, Any?>(
            "AVFormatIDKey" to 1633772320L,
            "AVSampleRateKey" to 44100.0,
            "AVNumberOfChannelsKey" to 1,
            "AVEncoderBitRateKey" to 64000
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
            println("Audio input added successfully")
        } else {
            println("Cannot add audio input - continuing without audio")
            audioWriterInput = null
        }

        assetWriter = writer
        videoWriterInput = videoInput

        return true
    }

    override fun switchCamera() {
        dispatch_async(sessionQueue) {
            captureSession.beginConfiguration()

            // Remove current video input
            captureVideoInput?.let {
                captureSession.removeInput(it)
            }

            // Toggle camera position
            currentCameraPosition =
                if (currentCameraPosition == AVCaptureDevicePositionBack)
                    AVCaptureDevicePositionFront
                else
                    AVCaptureDevicePositionBack

            val newDevice = getCameraDevice(currentCameraPosition)
                ?: run {
                    captureSession.commitConfiguration()
                    return@dispatch_async
                }

            val newInput = AVCaptureDeviceInput.deviceInputWithDevice(newDevice, null)
                ?: run {
                    captureSession.commitConfiguration()
                    return@dispatch_async
                }

            if (captureSession.canAddInput(newInput)) {
                captureSession.addInput(newInput)
                captureVideoInput = newInput
            }

            // Update video connection for front camera mirroring and orientation
            videoDataOutput.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                if (connection.supportsVideoMirroring) {
                    connection.automaticallyAdjustsVideoMirroring = false
                    connection.videoMirrored = (currentCameraPosition == AVCaptureDevicePositionFront)
                }
                if (connection.supportsVideoOrientation) {
                    connection.videoOrientation = AVCaptureVideoOrientationPortrait
                }
            }

            captureSession.commitConfiguration()

            println("Camera switched to: ${if (currentCameraPosition == AVCaptureDevicePositionFront) "Front" else "Back"}")
            println("Recording continues: $isRecording")
        }
    }

    override fun startCamera() {
        dispatch_async(sessionQueue) {
            captureSession.beginConfiguration()

            // Video input
            if (captureVideoInput == null) {
                val videoDevice = getCameraDevice(currentCameraPosition)
                    ?: return@dispatch_async

                val input = AVCaptureDeviceInput.deviceInputWithDevice(videoDevice, null)
                    ?: return@dispatch_async

                if (captureSession.canAddInput(input)) {
                    captureSession.addInput(input)
                    captureVideoInput = input
                }
            }

            // Audio input
            if (captureAudioInput == null) {
                val audioDevice = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
                val audioInput = audioDevice?.let {
                    AVCaptureDeviceInput.deviceInputWithDevice(it, null)
                }

                audioInput?.let {
                    if (captureSession.canAddInput(it)) {
                        captureSession.addInput(it)
                        captureAudioInput = it
                    }
                }
            }

            // Video data output - use the writer queue for processing
            if (!captureSession.outputs.contains(videoDataOutput)) {
                if (captureSession.canAddOutput(videoDataOutput)) {
                    captureSession.addOutput(videoDataOutput)
                    videoDataOutput.setSampleBufferDelegate(videoOutputDelegate, writerQueue)

                    // Configure video connection for portrait orientation
                    videoDataOutput.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                        if (connection.supportsVideoOrientation) {
                            connection.videoOrientation = AVCaptureVideoOrientationPortrait
                        }
                        if (connection.supportsVideoMirroring) {
                            connection.automaticallyAdjustsVideoMirroring = false
                            connection.videoMirrored = (currentCameraPosition == AVCaptureDevicePositionFront)
                        }
                    }
                }
            }

            // Audio data output - use the same writer queue
            if (!captureSession.outputs.contains(audioDataOutput)) {
                if (captureSession.canAddOutput(audioDataOutput)) {
                    captureSession.addOutput(audioDataOutput)
                    audioDataOutput.setSampleBufferDelegate(audioOutputDelegate, writerQueue)
                }
            }

            captureSession.commitConfiguration()

            previewLayer.session = captureSession
            previewLayer.connection?.videoOrientation = AVCaptureVideoOrientationPortrait

            if (!captureSession.running) {
                captureSession.startRunning()
            }

            dispatch_async(dispatch_get_main_queue()) {
                onCameraReady?.invoke()
            }
        }
    }

    override fun startRecording() {
        println("startRecording called, captureSession.running=${captureSession.running}")

        if (!captureSession.running) {
            println("Capture session not ready yet")
            return
        }

        if (isRecording) {
            println("Already recording")
            return
        }

        val fileUrl = generateVideoFileUrl()
        currentVideoUrl = fileUrl
        println("Video URL: $fileUrl")

        // Remove existing file if present
        NSFileManager.defaultManager.removeItemAtURL(fileUrl, null)

        if (!setupAssetWriter(fileUrl)) {
            println("Failed to setup asset writer")
            return
        }
        println("Asset writer setup successful")

        sessionStarted = false
        frameCount = 0
        isRecording = true
        println("Recording flag set to true, waiting for frames...")
    }

    override fun stopRecording() {
        println("stopRecording called, isRecording=$isRecording, sessionStarted=$sessionStarted, frameCount=$frameCount")

        if (!isRecording) {
            println("Not recording")
            return
        }

        isRecording = false

        val writer = assetWriter
        val url = currentVideoUrl

        if (writer == null || url == null) {
            println("No writer or URL - writer=$writer, url=$url")
            return
        }

        println("Writer status before finish: ${writer.status}")

        // Finish writing on the writer queue to ensure all buffers are processed
        dispatch_async(writerQueue) {
            println("Finishing writing on writer queue...")
            videoWriterInput?.markAsFinished()
            audioWriterInput?.markAsFinished()

            writer.finishWritingWithCompletionHandler {
                dispatch_async(dispatch_get_main_queue()) {
                    val path = url.path ?: return@dispatch_async
                    val fileName = path.substringAfterLast("/")
                    val fileSize = getFileSize(url)

                    println("=== Recording Complete ===")
                    println("Video saved to: $url")
                    println("Video size: $fileSize bytes")
                    println("Writer status: ${writer.status}")
                    println("Total frames: $frameCount")

                    if (writer.status == AVAssetWriterStatusFailed) {
                        println("Writer error: ${writer.error}")
                    }

                    this.assetWriter = null
                    this.videoWriterInput = null
                    this.audioWriterInput = null
                    this.currentVideoUrl = null
                    this.sessionStarted = false

                    onVideoRecorded?.invoke(fileName, fileSize)
                }
            }
        }
    }

    override fun stopCamera() {
        dispatch_async(sessionQueue) {
            if (isRecording) {
                stopRecording()
            }
            captureSession.stopRunning()
        }
    }
}
