package org.example.bubbleapp.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

interface FileSystemManager {
    fun getDocumentsDirectory(): String
    fun getVideoFilePath(fileName: String): String
    fun getVideoFileUrl(fileName: String): NSURL
    fun generateVideoFileName(): String
    fun getFileSize(url: NSURL): Long
    fun getRecordedVideos(): List<String>
    fun deleteFile(url: NSURL): Boolean
    fun fileExists(path: String): Boolean
}

@OptIn(ExperimentalForeignApi::class)
class IOSFileSystemManager : FileSystemManager {

    private val fileManager: NSFileManager
        get() = NSFileManager.defaultManager

    override fun getDocumentsDirectory(): String {
        return NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String
            ?: NSTemporaryDirectory()
    }

    override fun getVideoFilePath(fileName: String): String {
        return "${getDocumentsDirectory()}/$fileName"
    }

    override fun getVideoFileUrl(fileName: String): NSURL {
        return NSURL.fileURLWithPath(getVideoFilePath(fileName))
    }

    override fun generateVideoFileName(): String {
        return "video_${NSDate().timeIntervalSince1970}.mp4"
    }

    override fun getFileSize(url: NSURL): Long {
        val path = url.path ?: return 0L
        val attributes = fileManager.attributesOfItemAtPath(path, null)
        return (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
    }

    override fun getRecordedVideos(): List<String> {
        val documentsDir = getDocumentsDirectory()
        val contents = fileManager.contentsOfDirectoryAtPath(documentsDir, null)
            ?: return emptyList()

        return contents
            .filterIsInstance<String>()
            .filter { it.startsWith("video_") && it.endsWith(".mp4") }
            .sortedDescending()
    }

    override fun deleteFile(url: NSURL): Boolean {
        return fileManager.removeItemAtURL(url, null)
    }

    override fun fileExists(path: String): Boolean {
        return fileManager.fileExistsAtPath(path)
    }
}
