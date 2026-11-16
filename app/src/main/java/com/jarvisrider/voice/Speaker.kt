package com.jarvisrider.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Short-lived TextToSpeech wrapper. Create one per interaction and call [shutdown]
 * as soon as the interaction ends so the TTS engine connection is released.
 */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var counter = 0

    /** True when a Hindi voice is active (prompts should then be Hindi). */
    var hindiActive = false
        private set

    suspend fun init(wantHindi: Boolean, usage: Int, rate: Float = 1.0f): Boolean {
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val once = Once(cont)
                tts = TextToSpeech(appContext) { status -> once.resume(status == TextToSpeech.SUCCESS) }
                cont.invokeOnCancellation { /* engine is released by shutdown() */ }
            }
        } ?: false
        val engine = tts
        if (!ok || engine == null) {
            shutdown()
            return false
        }
        hindiActive = wantHindi && setLanguage(engine, Locale.forLanguageTag("hi-IN"))
        if (!hindiActive && !setLanguage(engine, Locale.forLanguageTag("en-IN"))) {
            setLanguage(engine, Locale.US)
        }
        try {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setSpeechRate(rate)
        } catch (_: RuntimeException) {
            // Engine refused attributes; it still speaks on its default stream.
        }
        return true
    }

    private fun setLanguage(engine: TextToSpeech, locale: Locale): Boolean = try {
        engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
    } catch (_: RuntimeException) {
        false
    }

    /** Speaks [text] and suspends until it finishes (or a length-based timeout). */
    suspend fun say(text: String): Boolean {
        val engine = tts ?: return false
        val id = "jr" + (++counter)
        val timeout = 3_000L + text.length * 120L
        val result = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val once = Once(cont)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id) once.resume(true)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id) once.resume(false)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == id) once.resume(false)
                    }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == id) once.resume(false)
                    }
                })
                val r = try {
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                } catch (_: RuntimeException) {
                    TextToSpeech.ERROR
                }
                if (r != TextToSpeech.SUCCESS) once.resume(false)
                cont.invokeOnCancellation { stopQuietly(engine) }
            }
        }
        if (result == null) stopQuietly(engine)
        return result ?: false
    }

    private fun stopQuietly(engine: TextToSpeech) {
        try {
            engine.stop()
        } catch (_: RuntimeException) {
        }
    }

    fun shutdown() {
        val engine = tts ?: return
        tts = null
        try {
            engine.stop()
            engine.shutdown()
        } catch (_: RuntimeException) {
        }
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
