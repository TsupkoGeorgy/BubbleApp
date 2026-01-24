package org.example.bubbleapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import org.example.bubbleapp.storage.FileSystemManager
import org.example.bubbleapp.storage.IOSFileSystemManager
import org.example.bubbleapp.video.IOSMediaEncodingManager
import org.example.bubbleapp.video.MediaEncodingManager
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import kotlin.concurrent.Volatile

@Composable
actual fun rememberCameraController(): CameraController {
    return remember { IOSCameraController() }
}

@OptIn(ExperimentalForeignApi::class)
class IOSCameraController(
    private val fileSystemManager: FileSystemManager = IOSFileSystemManager(),
    private val encodingManager: MediaEncodingManager = IOSMediaEncodingManager()
) : CameraController {

    private var captureVideoInput: AVCaptureDeviceInput? = null
    private var captureAudioInput: AVCaptureDeviceInput? = null
    private var currentCameraPosition = AVCaptureDevicePositionBack

    @Volatile
    private var isRecording = false
    private var currentVideoUrl: NSURL? = null

    private val sessionQueue: dispatch_queue_t =
        dispatch_queue_create("com.bubbleapp.session", null)

    private val captureSession = AVCaptureSession().apply {
        sessionPreset = AVCaptureSessionPreset1280x720
    }

    val previewLayer = AVCaptureVideoPreviewLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    private val videoDataOutput = AVCaptureVideoDataOutput().apply {
        alwaysDiscardsLateVideoFrames = true
        videoSettings = mapOf<Any?, Any?>(
            kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA
        )
    }

    private val audioDataOutput = AVCaptureAudioDataOutput()

    override var onVideoRecorded: ((fileName: String, fileSize: Long) -> Unit)? = null
    override var onCameraReady: (() -> Unit)? = null

    // Delegates
    private val videoOutputDelegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection
        ) {
            if (!isRecording) return
            didOutputSampleBuffer?.let { buffer ->
                encodingManager.appendVideoSample(buffer)
            }
        }
    }

    private val audioOutputDelegate = object : NSObject(), AVCaptureAudioDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection
        ) {
            if (!isRecording) return
            didOutputSampleBuffer?.let { buffer ->
                encodingManager.appendAudioSample(buffer)
            }
        }
    }

    // Camera device discovery
    private fun getCameraDevice(position: AVCaptureDevicePosition): AVCaptureDevice? {
        val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position
        )
        return discovery.devices.firstOrNull() as? AVCaptureDevice
    }

    override fun createPreviewController(): Any {
        return CameraViewController(this)
    }

    override fun createInlinePlayer(fileName: String): InlineVideoViewController {
        val url = fileSystemManager.getVideoFileUrl(fileName)
        return InlineVideoViewController(url)
    }

    override fun getRecordedVideos(): List<String> {
        return fileSystemManager.getRecordedVideos()
    }

    override fun playVideo(fileName: String) {
        val url = fileSystemManager.getVideoFileUrl(fileName)
        val player = AVPlayer(uRL = url)
        val playerVC = AVPlayerViewController().apply {
            this.player = player
        }

        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        rootVC.presentViewController(playerVC, animated = true) {
            player.play()
        }
    }

    override fun switchCamera() {
        dispatch_async(sessionQueue) {
            captureSession.beginConfiguration()

            captureVideoInput?.let { captureSession.removeInput(it) }

            currentCameraPosition = if (currentCameraPosition == AVCaptureDevicePositionBack)
                AVCaptureDevicePositionFront
            else
                AVCaptureDevicePositionBack

            val newDevice = getCameraDevice(currentCameraPosition) ?: run {
                captureSession.commitConfiguration()
                return@dispatch_async
            }

            val newInput = AVCaptureDeviceInput.deviceInputWithDevice(newDevice, null) ?: run {
                captureSession.commitConfiguration()
                return@dispatch_async
            }

            if (captureSession.canAddInput(newInput)) {
                captureSession.addInput(newInput)
                captureVideoInput = newInput
            }

            configureVideoConnection()
            captureSession.commitConfiguration()
        }
    }

    override fun setZoom(factor: Float) {
        dispatch_async(sessionQueue) {
            val device = captureVideoInput?.device ?: return@dispatch_async
            val maxZoom = device.activeFormat.videoMaxZoomFactor.toFloat()
            val clampedFactor = factor.coerceIn(1f, minOf(maxZoom, 5f))

            try {
                device.lockForConfiguration(null)
                device.videoZoomFactor = clampedFactor.toDouble()
                device.unlockForConfiguration()
            } catch (e: Exception) {
                println("IOSCameraController: Failed to set zoom: ${e.message}")
            }
        }
    }

    override fun startCamera() {
        dispatch_async(sessionQueue) {
            captureSession.beginConfiguration()

            setupVideoInput()
            setupAudioInput()
            setupVideoOutput()
            setupAudioOutput()

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

    private fun setupVideoInput() {
        if (captureVideoInput != null) return

        val videoDevice = getCameraDevice(currentCameraPosition) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(videoDevice, null) ?: return

        if (captureSession.canAddInput(input)) {
            captureSession.addInput(input)
            captureVideoInput = input
        }
    }

    private fun setupAudioInput() {
        if (captureAudioInput != null) return

        val audioDevice = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(audioDevice, null) ?: return

        if (captureSession.canAddInput(input)) {
            captureSession.addInput(input)
            captureAudioInput = input
        }
    }

    private fun setupVideoOutput() {
        if (captureSession.outputs.contains(videoDataOutput)) return

        if (captureSession.canAddOutput(videoDataOutput)) {
            captureSession.addOutput(videoDataOutput)
            videoDataOutput.setSampleBufferDelegate(videoOutputDelegate, encodingManager.writerQueue)
            configureVideoConnection()
        }
    }

    private fun setupAudioOutput() {
        if (captureSession.outputs.contains(audioDataOutput)) return

        if (captureSession.canAddOutput(audioDataOutput)) {
            captureSession.addOutput(audioDataOutput)
            audioDataOutput.setSampleBufferDelegate(audioOutputDelegate, encodingManager.writerQueue)
        }
    }

    private fun configureVideoConnection() {
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

    override fun startRecording() {
        dispatch_async(sessionQueue) {
            if (!captureSession.running || isRecording) return@dispatch_async

            val fileName = fileSystemManager.generateVideoFileName()
            val fileUrl = fileSystemManager.getVideoFileUrl(fileName)
            currentVideoUrl = fileUrl

            // Remove existing file if present
            fileSystemManager.deleteFile(fileUrl)

            if (!encodingManager.startEncoding(fileUrl)) {
                println("IOSCameraController: Failed to start encoding")
                return@dispatch_async
            }

            isRecording = true
            println("IOSCameraController: Recording started")
        }
    }

    override fun stopRecording() {
        dispatch_async(sessionQueue) {
            if (!isRecording) return@dispatch_async
            isRecording = false

            val url = currentVideoUrl ?: return@dispatch_async

            encodingManager.finishEncoding { success, error ->
                if (success) {
                    val path = url.path ?: return@finishEncoding
                    val fileName = path.substringAfterLast("/")
                    val fileSize = fileSystemManager.getFileSize(url)

                    println("IOSCameraController: Recording saved - $fileName ($fileSize bytes)")
                    onVideoRecorded?.invoke(fileName, fileSize)
                } else {
                    println("IOSCameraController: Recording failed - $error")
                }

                currentVideoUrl = null
            }
        }
    }

    override fun stopCamera() {
        dispatch_async(sessionQueue) {
            if (isRecording) {
                isRecording = false
                encodingManager.cancelEncoding()
            }
            captureSession.stopRunning()
        }
    }
}

// Camera preview UIViewController
class CameraViewController(
    private val controller: IOSCameraController
) : UIViewController(null, null) {

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.clearColor
        view.layer.addSublayer(controller.previewLayer)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        controller.previewLayer.frame = view.bounds
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        controller.startCamera()
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        // Cleanup when view disappears
    }
}
