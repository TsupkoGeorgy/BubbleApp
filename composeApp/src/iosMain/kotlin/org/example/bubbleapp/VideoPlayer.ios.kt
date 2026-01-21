package org.example.bubbleapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import org.example.bubbleapp.storage.IOSFileSystemManager
import org.example.bubbleapp.video.IOSThumbnailGenerator
import org.example.bubbleapp.video.ThumbnailGenerator
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
