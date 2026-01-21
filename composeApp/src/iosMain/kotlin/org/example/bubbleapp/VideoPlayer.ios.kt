package org.example.bubbleapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import org.example.bubbleapp.storage.IOSFileSystemManager
import org.example.bubbleapp.video.IOSThumbnailGenerator
import org.example.bubbleapp.video.ThumbnailGenerator
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.NSObject

// Singleton instances for reuse
private val fileSystemManager = IOSFileSystemManager()
private val thumbnailGenerator: ThumbnailGenerator = IOSThumbnailGenerator(fileSystemManager)

class InlineVideoViewController(
    private val videoUrl: NSURL
) : UIViewController(null, null) {

    private val player = AVPlayer(uRL = videoUrl)
    private val playerVC = AVPlayerViewController().apply {
        player = this@InlineVideoViewController.player
        showsPlaybackControls = false
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        addChildViewController(playerVC)
        view.addSubview(playerVC.view)
        playerVC.didMoveToParentViewController(this)
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        player.play()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        playerVC.view.setFrame(view.bounds)
    }

    fun play() {
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

actual class InlineVideoPlayer actual constructor(fileName: String) {
    val viewController: InlineVideoViewController

    init {
        val url = fileSystemManager.getVideoFileUrl(fileName)
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
        factory = { player.viewController }
    )
}

@Composable
actual fun VideoThumbnail(
    fileName: String,
    modifier: Modifier
) {
    var thumbnail by remember(fileName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(fileName) {
        // Check cache first, then generate if needed
        thumbnail = thumbnailGenerator.getCachedThumbnail(fileName)
            ?: thumbnailGenerator.generateThumbnail(fileName)
    }

    Box(
        modifier = modifier.background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        thumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// Silent looping video preview player with ready state tracking
class VideoPreviewViewController(
    private val videoUrl: NSURL,
    private val onReadyToPlay: () -> Unit
) : UIViewController(null, null) {

    private val playerItem = AVPlayerItem(uRL = videoUrl)
    private val player = AVPlayer(playerItem = playerItem).apply {
        setMuted(true)
    }

    private val playerVC = AVPlayerViewController().apply {
        player = this@VideoPreviewViewController.player
        showsPlaybackControls = false
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    private var loopObserver: Any? = null
    private var isReady = false

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.clearColor
        playerVC.view.backgroundColor = UIColor.clearColor

        // Hide player view until ready
        playerVC.view.alpha = 0.0

        addChildViewController(playerVC)
        view.addSubview(playerVC.view)
        playerVC.didMoveToParentViewController(this)

        // Setup loop - seek to beginning when video ends
        loopObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = playerItem,
            queue = null
        ) { _ ->
            val zeroTime = CMTimeMake(value = 0, timescale = 1)
            player.seekToTime(zeroTime)
            player.play()
        }

        // Check status periodically until ready
        checkReadyStatus()
    }

    private fun checkReadyStatus() {
        if (isReady) return

        if (playerItem.status == AVPlayerItemStatusReadyToPlay) {
            isReady = true
            dispatch_async(dispatch_get_main_queue()) {
                player.play()
                onReadyToPlay()
            }
        } else {
            // Check again after short delay
            dispatch_async(dispatch_get_main_queue()) {
                if (!isReady) {
                    checkReadyStatus()
                }
            }
        }
    }

    fun showPlayer() {
        dispatch_async(dispatch_get_main_queue()) {
            UIView.animateWithDuration(0.3) {
                playerVC.view.alpha = 1.0
            }
        }
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        if (isReady) {
            player.play()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        playerVC.view.setFrame(view.bounds)
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        player.pause()
    }

    fun cleanup() {
        isReady = true // Stop checking
        loopObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        loopObserver = null
        player.pause()
    }
}

@Composable
actual fun VideoPreviewPlayer(
    fileName: String,
    modifier: Modifier
) {
    var isVideoReady by remember(fileName) { mutableStateOf(false) }
    var minDelayPassed by remember(fileName) { mutableStateOf(false) }

    // Minimum delay for smooth UX
    LaunchedEffect(fileName) {
        kotlinx.coroutines.delay(1500)
        minDelayPassed = true
    }

    val url = remember(fileName) { fileSystemManager.getVideoFileUrl(fileName) }
    val controller = remember(fileName) {
        VideoPreviewViewController(url) {
            isVideoReady = true
        }
    }

    DisposableEffect(fileName) {
        onDispose {
            controller.cleanup()
        }
    }

    val showVideo = isVideoReady && minDelayPassed

    Box(
        modifier = modifier.background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        // Video layer (hidden until ready via alpha in controller)
        UIKitViewController(
            modifier = Modifier.fillMaxSize(),
            factory = { controller }
        )

        // Loading indicator until video is ready AND min delay passed
        if (!showVideo) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        }
    }

    // Show video only after both conditions met
    LaunchedEffect(showVideo) {
        if (showVideo) {
            controller.showPlayer()
        }
    }
}
