package org.example.bubbleapp.speech

import kotlinx.cinterop.ExperimentalForeignApi
import org.example.bubbleapp.storage.IOSFileSystemManager
import platform.AVFoundation.*
import platform.Foundation.*
import platform.Speech.*
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

enum class TranscriptionState {
    Idle,
    Loading,
    Success,
    Error
}

data class TranscriptionResult(
    val state: TranscriptionState,
    val text: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalForeignApi::class)
class SpeechTranscriber {

    private val fileSystemManager = IOSFileSystemManager()

    // Пробуем сначала русский, потом английский
    private val russianRecognizer: SFSpeechRecognizer? = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "ru-RU"))
    private val englishRecognizer: SFSpeechRecognizer? = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "en-US"))

    private val speechRecognizer: SFSpeechRecognizer?
        get() = russianRecognizer ?: englishRecognizer

    // Кэш транскрипций
    private val transcriptionCache = mutableMapOf<String, String>()

    fun hasTranscription(fileName: String): Boolean {
        return transcriptionCache.containsKey(fileName)
    }

    fun getCachedTranscription(fileName: String): String? {
        return transcriptionCache[fileName]
    }

    fun transcribe(
        fileName: String,
        onResult: (TranscriptionResult) -> Unit
    ) {
        // Проверяем кэш
        transcriptionCache[fileName]?.let { cached ->
            onResult(TranscriptionResult(TranscriptionState.Success, cached))
            return
        }

        // Проверяем доступность хотя бы одного распознавателя
        val recognizer = russianRecognizer ?: englishRecognizer
        if (recognizer == null || !recognizer.isAvailable()) {
            onResult(TranscriptionResult(TranscriptionState.Error, error = "Распознавание речи недоступно"))
            return
        }

        // Запрашиваем разрешение
        SFSpeechRecognizer.requestAuthorization { status ->
            dispatch_async(dispatch_get_main_queue()) {
                when (status) {
                    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized -> {
                        // Пробуем напрямую с MP4 файлом
                        this.performTranscriptionDirect(fileName, onResult)
                    }
                    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusDenied -> {
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Доступ к распознаванию запрещён"))
                    }
                    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusRestricted -> {
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Распознавание ограничено"))
                    }
                    else -> {
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Статус неопределён"))
                    }
                }
            }
        }
    }

    private fun performTranscriptionDirect(
        fileName: String,
        onResult: (TranscriptionResult) -> Unit
    ) {
        val videoUrl = fileSystemManager.getVideoFileUrl(fileName)
        println("SpeechTranscriber: Starting transcription for $videoUrl")

        // Сначала пробуем русский
        tryTranscribeWithRecognizer(russianRecognizer, "ru-RU", fileName, videoUrl) { russianResult ->
            if (russianResult.state == TranscriptionState.Success) {
                onResult(russianResult)
            } else {
                println("SpeechTranscriber: Russian failed, trying English after delay...")
                // Задержка перед вторым запросом чтобы файл освободился
                val delay = dispatch_time(DISPATCH_TIME_NOW, (500 * NSEC_PER_MSEC.toLong()))
                dispatch_after(delay, dispatch_get_main_queue()) {
                    tryTranscribeWithRecognizer(englishRecognizer, "en-US", fileName, videoUrl) { englishResult ->
                        onResult(englishResult)
                    }
                }
            }
        }
    }

    private fun tryTranscribeWithRecognizer(
        recognizer: SFSpeechRecognizer?,
        locale: String,
        fileName: String,
        videoUrl: NSURL,
        onResult: (TranscriptionResult) -> Unit
    ) {
        if (recognizer == null || !recognizer.isAvailable()) {
            onResult(TranscriptionResult(TranscriptionState.Error, error = "Распознаватель $locale недоступен"))
            return
        }

        println("SpeechTranscriber: Trying $locale recognizer")

        val request = SFSpeechURLRecognitionRequest(uRL = videoUrl)
        request.setShouldReportPartialResults(false)

        recognizer.recognitionTaskWithRequest(request) { result, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (error != null) {
                    val isNoSpeech = error.localizedDescription.contains("no speech", ignoreCase = true)
                    println("SpeechTranscriber: $locale error - ${error.localizedDescription}")

                    if (isNoSpeech) {
                        // "No speech" - попробуем другой язык
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Речь не обнаружена ($locale)"))
                    } else {
                        onResult(TranscriptionResult(TranscriptionState.Error, error = error.localizedDescription))
                    }
                    return@dispatch_async
                }

                result?.let { res ->
                    if (res.isFinal()) {
                        val transcribedText = res.bestTranscription.formattedString
                        println("SpeechTranscriber: $locale result: '$transcribedText'")

                        if (transcribedText.isNotEmpty()) {
                            transcriptionCache[fileName] = transcribedText
                            onResult(TranscriptionResult(TranscriptionState.Success, transcribedText))
                        } else {
                            onResult(TranscriptionResult(TranscriptionState.Error, error = "Текст не распознан ($locale)"))
                        }
                    }
                }
            }
        }
    }

    private fun extractAudioAndTranscribe(
        fileName: String,
        onResult: (TranscriptionResult) -> Unit
    ) {
        val videoUrl = fileSystemManager.getVideoFileUrl(fileName)

        // Создаём путь для временного аудиофайла
        val audioFileName = fileName.replace(".mp4", "_audio.m4a")
        val audioUrl = fileSystemManager.getVideoFileUrl(audioFileName)

        // Удаляем старый аудиофайл если существует
        NSFileManager.defaultManager.removeItemAtURL(audioUrl, null)

        // Создаём asset из видео
        val asset = AVURLAsset(uRL = videoUrl, options = null)

        // Проверяем есть ли аудиодорожка
        val audioTracks = asset.tracksWithMediaType(AVMediaTypeAudio)
        if (audioTracks.isEmpty()) {
            onResult(TranscriptionResult(TranscriptionState.Error, error = "В видео нет аудиодорожки"))
            return
        }

        // Создаём экспортер для извлечения аудио
        val exporter = AVAssetExportSession(asset = asset, presetName = AVAssetExportPresetAppleM4A)
        if (exporter == null) {
            onResult(TranscriptionResult(TranscriptionState.Error, error = "Не удалось создать экспортер"))
            return
        }

        exporter.outputURL = audioUrl
        exporter.outputFileType = AVFileTypeAppleM4A

        println("SpeechTranscriber: Extracting audio from $fileName")

        exporter.exportAsynchronouslyWithCompletionHandler {
            dispatch_async(dispatch_get_main_queue()) {
                when (exporter.status) {
                    AVAssetExportSessionStatusCompleted -> {
                        println("SpeechTranscriber: Audio extracted successfully")
                        this.performTranscription(fileName, audioUrl, onResult)
                    }
                    AVAssetExportSessionStatusFailed -> {
                        val error = exporter.error?.localizedDescription ?: "Неизвестная ошибка"
                        println("SpeechTranscriber: Audio extraction failed: $error")
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Ошибка извлечения аудио: $error"))
                    }
                    AVAssetExportSessionStatusCancelled -> {
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Извлечение отменено"))
                    }
                    else -> {
                        onResult(TranscriptionResult(TranscriptionState.Error, error = "Неожиданный статус экспорта"))
                    }
                }
            }
        }
    }

    private fun performTranscription(
        originalFileName: String,
        audioUrl: NSURL,
        onResult: (TranscriptionResult) -> Unit
    ) {
        println("SpeechTranscriber: Starting transcription for $audioUrl")

        // Создаём запрос для распознавания из аудиофайла
        val request = SFSpeechURLRecognitionRequest(uRL = audioUrl)

        // Пока используем серверное распознавание - оно точнее
        // TODO: можно включить офлайн после тестирования
        // if (speechRecognizer?.supportsOnDeviceRecognition() == true) {
        //     request.setRequiresOnDeviceRecognition(true)
        // }
        println("SpeechTranscriber: Using server-based recognition")

        // Отключаем частичные результаты - нам нужен только финальный
        request.setShouldReportPartialResults(false)

        // Запускаем распознавание
        speechRecognizer?.recognitionTaskWithRequest(request) { result, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (error != null) {
                    val errorMessage = when {
                        error.localizedDescription.contains("no speech", ignoreCase = true) -> "Речь не обнаружена"
                        error.localizedDescription.contains("network", ignoreCase = true) -> "Требуется интернет"
                        error.localizedDescription.contains("retry", ignoreCase = true) -> "Попробуйте позже"
                        else -> error.localizedDescription
                    }
                    println("SpeechTranscriber: Error - $errorMessage")
                    onResult(TranscriptionResult(TranscriptionState.Error, error = errorMessage))

                    // Удаляем временный аудиофайл
                    NSFileManager.defaultManager.removeItemAtURL(audioUrl, null)
                    return@dispatch_async
                }

                result?.let { res ->
                    if (res.isFinal()) {
                        val transcribedText = res.bestTranscription.formattedString
                        println("SpeechTranscriber: Transcription result: '$transcribedText'")

                        if (transcribedText.isNotEmpty()) {
                            // Сохраняем в кэш
                            transcriptionCache[originalFileName] = transcribedText
                            onResult(TranscriptionResult(TranscriptionState.Success, transcribedText))
                        } else {
                            onResult(TranscriptionResult(TranscriptionState.Error, error = "Текст не распознан"))
                        }

                        // Удаляем временный аудиофайл
                        NSFileManager.defaultManager.removeItemAtURL(audioUrl, null)
                    }
                }
            }
        }
    }

    companion object {
        val shared = SpeechTranscriber()
    }
}
