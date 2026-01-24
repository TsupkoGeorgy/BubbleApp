package org.example.bubbleapp

import org.example.bubbleapp.speech.SpeechTranscriber
import org.example.bubbleapp.speech.TranscriptionState as IOSTranscriptionState

actual fun transcribeVideo(fileName: String, onResult: (TranscriptionResult) -> Unit) {
    SpeechTranscriber.shared.transcribe(fileName) { result ->
        val state = when (result.state) {
            IOSTranscriptionState.Idle -> TranscriptionState.Idle
            IOSTranscriptionState.Loading -> TranscriptionState.Loading
            IOSTranscriptionState.Success -> TranscriptionState.Success
            IOSTranscriptionState.Error -> TranscriptionState.Error
        }
        onResult(TranscriptionResult(state, result.text, result.error))
    }
}

actual fun getCachedTranscription(fileName: String): String? {
    return SpeechTranscriber.shared.getCachedTranscription(fileName)
}
