package org.example.bubbleapp.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.example.bubbleapp.storage.FileSystemManager
import org.jetbrains.skia.Image
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

interface ThumbnailGenerator {
    fun generateThumbnail(fileName: String): ImageBitmap?
    fun getCachedThumbnail(fileName: String): ImageBitmap?
    fun clearCache()
}

class IOSThumbnailGenerator(
    private val fileSystemManager: FileSystemManager
) : ThumbnailGenerator {

    private val cache = mutableMapOf<String, ImageBitmap>()

    override fun getCachedThumbnail(fileName: String): ImageBitmap? {
        return cache[fileName]
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun generateThumbnail(fileName: String): ImageBitmap? {
        // Check cache first
        cache[fileName]?.let { return it }

        val url = fileSystemManager.getVideoFileUrl(fileName)
        val bitmap = generateThumbnailFromUrl(url)

        // Cache the result
        bitmap?.let { cache[fileName] = it }

        return bitmap
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun generateThumbnailFromUrl(url: NSURL): ImageBitmap? {
        val asset = AVAsset.assetWithURL(url)
        val imageGenerator = AVAssetImageGenerator(asset = asset).apply {
            appliesPreferredTrackTransform = true
        }

        val time = CMTimeMake(value = 0, timescale = 1)
        val cgImage = try {
            imageGenerator.copyCGImageAtTime(time, actualTime = null, error = null)
        } catch (e: Exception) {
            println("ThumbnailGenerator: Failed to generate thumbnail for $url: ${e.message}")
            null
        } ?: return null

        val uiImage = UIImage.imageWithCGImage(cgImage)
        val pngData = UIImagePNGRepresentation(uiImage) ?: return null

        val bytes = pngData.toByteArray()

        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Exception) {
            println("ThumbnailGenerator: Failed to decode image: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val bytes = ByteArray(length.toInt())
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this@toByteArray.bytes, length)
        }
        return bytes
    }

    override fun clearCache() {
        cache.clear()
    }
}
