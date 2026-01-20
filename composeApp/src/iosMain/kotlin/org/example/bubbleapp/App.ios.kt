package org.example.bubbleapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.UIKit.*
import platform.Foundation.*
import platform.QuartzCore.CALayer
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberCameraController(): CameraController {
    val controller = remember { IOSCameraController() }
    return controller
}

@OptIn(ExperimentalForeignApi::class)
class IOSCameraController : CameraController {

    private lateinit var assetWriter: AVAssetWriter
    private var cameraVideoInput: AVCaptureDeviceInput? = null
    private lateinit var videoWriterInput: AVAssetWriterInput
    private lateinit var audioWriterInput: AVAssetWriterInput
    private var isWriting = false



    private var currentCameraPosition = AVCaptureDevicePositionBack

    private fun setupWriter(url: NSURL) {
        assetWriter = AVAssetWriter(url, AVFileTypeMPEG4, null)

        videoInput = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = mapOf(
                AVVideoCodecKey to AVVideoCodecTypeH264,
                AVVideoWidthKey to 720,
                AVVideoHeightKey to 1280
            )
        ).apply {
            expectsMediaDataInRealTime = true
        }

        audioInput = AVAssetWriterInput(
            mediaType = AVMediaTypeAudio,
            outputSettings = null
        ).apply {
            expectsMediaDataInRealTime = true
        }

        assetWriter.addInput(videoInput)
        assetWriter.addInput(audioInput)
    }


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
        sessionPreset = AVCaptureSessionPresetHigh
    }


    val previewLayer = AVCaptureVideoPreviewLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    private val movieOutput = AVCaptureMovieFileOutput()

    override var onVideoRecorded: ((fileName: String, fileSize: Long) -> Unit)? = null

    override var onCameraReady: (() -> Unit)? = null

    private val recordingDelegate =
        object : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {

            override fun captureOutput(
                output: AVCaptureFileOutput,
                didFinishRecordingToOutputFileAtURL: NSURL,
                fromConnections: List<*>,
                error: NSError?
            ) {
                val path = didFinishRecordingToOutputFileAtURL.path ?: return
                val fileName = path.substringAfterLast("/")
                val fileSize = getFileSize(didFinishRecordingToOutputFileAtURL)

                println("Video saved to: $didFinishRecordingToOutputFileAtURL")
                println("Video size: $fileSize bytes")

                // Передаём в Compose
                onVideoRecorded?.invoke(fileName, fileSize)
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

    override fun switchCamera() {
        dispatch_async(dispatch_get_main_queue()) {

            captureSession.beginConfiguration()

            videoInput?.let {
                captureSession.removeInput(it)
            }

            currentCameraPosition =
                if (currentCameraPosition == AVCaptureDevicePositionBack)
                    AVCaptureDevicePositionFront
                else
                    AVCaptureDevicePositionBack

            val newDevice = getCameraDevice(currentCameraPosition) ?: return@dispatch_async
            val newInput =
                AVCaptureDeviceInput.deviceInputWithDevice(newDevice, null)
                    ?: return@dispatch_async

            if (captureSession.canAddInput(newInput)) {
                captureSession.addInput(newInput)
                videoInput = newInput
            }

            captureSession.commitConfiguration()
        }
    }

    override fun startCamera() {
        dispatch_async(dispatch_get_main_queue()) {

            captureSession.beginConfiguration()

            // 🎥 VIDEO
            if (videoInput == null) {
                val videoDevice = getCameraDevice(currentCameraPosition)
                    ?: return@dispatch_async

                val input =
                    AVCaptureDeviceInput.deviceInputWithDevice(videoDevice, null)
                        ?: return@dispatch_async

                if (captureSession.canAddInput(input)) {
                    captureSession.addInput(input)
                    videoInput = input
                }
            }

            // 🎙 AUDIO
            val audioDevice =
                AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)

            val audioInput = audioDevice?.let {
                AVCaptureDeviceInput.deviceInputWithDevice(it, null)
            }

            audioInput?.let {
                if (!captureSession.inputs.contains(it) &&
                    captureSession.canAddInput(it)
                ) {
                    captureSession.addInput(it)
                }
            }

            // 🎥 OUTPUT
            if (!captureSession.outputs.contains(movieOutput)) {
                captureSession.addOutput(movieOutput)
            }

            captureSession.commitConfiguration()

            previewLayer.session = captureSession
            previewLayer.connection?.videoOrientation =
                AVCaptureVideoOrientationPortrait

            if (!captureSession.running) {
                captureSession.startRunning()
            }

            onCameraReady?.invoke()
        }
    }

    override fun startRecording() {
        if (!captureSession.running) {
            println("Capture session not ready yet")
            return
        }
        if (!movieOutput.recording) {
            val fileUrl = generateVideoFileUrl()
            movieOutput.startRecordingToOutputFileURL(fileUrl, recordingDelegate)
        }
    }

    override fun stopRecording() {
        if (movieOutput.recording) {
            movieOutput.stopRecording()
        }
    }

    override fun stopCamera() {
        dispatch_async(dispatch_get_main_queue()) {
            captureSession.stopRunning()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    cameraController: CameraController,
    onPreviewReady: (() -> Unit)?
) {
    val iosController = cameraController as IOSCameraController

    UIKitViewController(
        modifier = modifier,
        factory = {
            val vc = CameraViewController(iosController)
            vc
        }
    )
}

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
}


class InlineVideoViewController(
    private val videoUrl: NSURL
) : UIViewController(null, null) {

    private val player = AVPlayer(uRL = videoUrl)
    private val playerVC = AVPlayerViewController().apply {
        player = this@InlineVideoViewController.player
        showsPlaybackControls = false // как в чате
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    override fun viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = UIColor.blackColor

        // 1️⃣ добавляем как child VC
        addChildViewController(playerVC)

        // 2️⃣ добавляем его VIEW
        view.addSubview(playerVC.view)

        // 3️⃣ подтверждаем добавление
        playerVC.didMoveToParentViewController(this)
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)

        // 🔥 ВОТ ТУТ
        player.play()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        playerVC.view.setFrame(view.bounds)
    }

    fun play() {
        // Важно: play вызываем **после layout**
        dispatch_async(dispatch_get_main_queue()) {
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        player.pause()
    }
}

actual class InlineVideoPlayer actual constructor(
    fileName: String
) {
    val viewController: InlineVideoViewController

    init {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() ?: error("Documents dir not found")

        val url = NSURL.fileURLWithPath("$documentsDir/$fileName")
        viewController = InlineVideoViewController(url)
    }

    actual fun play() {
        viewController.play()
    }

    actual fun pause() {
        viewController.pause()
    }
}

@Composable
actual fun InlineVideoPlayerView(
    modifier: Modifier,
    player: InlineVideoPlayer
) {
    UIKitViewController(
        modifier = modifier,
        factory = { player.viewController },

        )
}