package org.example.bubbleapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
        factory = { player.viewController }
    )
}
