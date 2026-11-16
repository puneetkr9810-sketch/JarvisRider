package com.jarvisrider.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

sealed class Heard {
    data class Text(val alternatives: List<String>) : Heard()
    data class Failed(val error: Int) : Heard()

    companion object {
        /** Our own code: no recognition engine installed. */
        const val ERROR_NO_ENGINE = -1
    }
}

/**
 * One-shot SpeechRecognizer wrapper. A new recognizer is created for every [listen] call
 * and destroyed right after, so nothing stays bound while idle. Must be called on the main thread.
 */
class Listener(context: Context) {
    private val ctx = context

    suspend fun listen(language: String, preferOffline: Boolean, timeoutMs: Long = 9_000L): Heard {
        val component = RecognizerPicker.pick(ctx)
        val recognizer = try {
            when {
                component != null -> SpeechRecognizer.createSpeechRecognizer(ctx, component)
                SpeechRecognizer.isRecognitionAvailable(ctx) -> SpeechRecognizer.createSpeechRecognizer(ctx)
                else -> null
            }
        } catch (_: RuntimeException) {
            null
        } ?: return Heard.Failed(Heard.ERROR_NO_ENGINE)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
        }

        try {
            return withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val once = Once<Heard>(cont)
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                        override fun onError(error: Int) = once.resume(Heard.Failed(error))
                        override fun onResults(results: Bundle?) {
                            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.filter { it.isNotBlank() }.orEmpty()
                            once.resume(if (list.isEmpty()) Heard.Failed(SpeechRecognizer.ERROR_NO_MATCH) else Heard.Text(list))
                        }
                    })
                    try {
                        recognizer.startListening(intent)
                    } catch (_: RuntimeException) {
                        once.resume(Heard.Failed(SpeechRecognizer.ERROR_CLIENT))
                    }
                }
            } ?: Heard.Failed(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        } finally {
            try {
                recognizer.cancel()
            } catch (_: RuntimeException) {
            }
            try {
                recognizer.destroy()
            } catch (_: RuntimeException) {
            }
        }
    }

    companion object {
        fun isPermissionError(error: Int) = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS

        fun isEngineProblem(error: Int): Boolean = error == Heard.ERROR_NO_ENGINE ||
            error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_SERVER
    }
}
