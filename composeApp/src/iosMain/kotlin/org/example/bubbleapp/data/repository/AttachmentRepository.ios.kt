package org.example.bubbleapp.data.repository

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual suspend fun uploadToS3(
    uploadUrl: String,
    filePath: String,
    contentType: String,
    onProgress: (Float) -> Unit
): Boolean = suspendCancellableCoroutine { continuation ->

    val url = NSURL.URLWithString(uploadUrl)
    if (url == null) {
        continuation.resume(false)
        return@suspendCancellableCoroutine
    }

    val fileUrl = NSURL.fileURLWithPath(filePath)
    val fileData = NSData.dataWithContentsOfURL(fileUrl)
    if (fileData == null) {
        println("S3Upload: Failed to read file at $filePath")
        continuation.resume(false)
        return@suspendCancellableCoroutine
    }

    val request = NSMutableURLRequest.requestWithURL(url).apply {
        setHTTPMethod("PUT")
        setValue(contentType, forHTTPHeaderField = "Content-Type")
        setValue(fileData.length.toString(), forHTTPHeaderField = "Content-Length")
        setHTTPBody(fileData)
    }

    val session = NSURLSession.sharedSession

    val task = session.dataTaskWithRequest(request) { _, response, error ->
        if (error != null) {
            println("S3Upload: Error - ${error.localizedDescription}")
            if (continuation.isActive) {
                continuation.resume(false)
            }
            return@dataTaskWithRequest
        }

        val httpResponse = response as? NSHTTPURLResponse
        val statusCode = httpResponse?.statusCode ?: 0

        println("S3Upload: Response status code: $statusCode")

        if (continuation.isActive) {
            // S3 returns 200 on successful PUT
            continuation.resume(statusCode in 200..299)
        }
    }

    // Note: For progress tracking, would need NSURLSessionDataDelegate
    // For now, simulate progress
    onProgress(0.5f)

    task.resume()

    continuation.invokeOnCancellation {
        task.cancel()
    }
}
