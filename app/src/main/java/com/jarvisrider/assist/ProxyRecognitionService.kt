package com.jarvisrider.assist

import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.jarvisrider.voice.RecognizerPicker

/**
 * Android requires every assistant to declare a RecognitionService, and selecting an assistant
 * can make that service the system-wide speech recognizer. Instead of breaking voice input for
 * other apps, this service forwards every request to the real engine (Google, Samsung, ...).
 */
class ProxyRecognitionService : RecognitionService() {

    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        val cb = listener ?: return
        releaseDelegate()
        val target = RecognizerPicker.pick(this)
        if (target == null || recognizerIntent == null) {
            safe { cb.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        val sr = try {
            SpeechRecognizer.createSpeechRecognizer(this, target)
        } catch (_: RuntimeException) {
            safe { cb.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        delegate = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = safe { cb.readyForSpeech(params ?: Bundle()) }
            override fun onBeginningOfSpeech() = safe { cb.beginningOfSpeech() }
            override fun onRmsChanged(rmsdB: Float) = safe { cb.rmsChanged(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) = safe { if (buffer != null) cb.bufferReceived(buffer) }
            override fun onEndOfSpeech() = safe { cb.endOfSpeech() }
            override fun onError(error: Int) {
                safe { cb.error(error) }
                releaseDelegate()
            }
            override fun onResults(results: Bundle?) {
                safe { cb.results(results ?: Bundle()) }
                releaseDelegate()
            }
            override fun onPartialResults(partialResults: Bundle?) = safe { cb.partialResults(partialResults ?: Bundle()) }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        try {
            sr.startListening(recognizerIntent)
        } catch (_: RuntimeException) {
            safe { cb.error(SpeechRecognizer.ERROR_CLIENT) }
            releaseDelegate()
        }
    }

    override fun onStopListening(listener: Callback?) {
        try {
            delegate?.stopListening()
        } catch (_: RuntimeException) {
        }
    }

    override fun onCancel(listener: Callback?) = releaseDelegate()

    override fun onDestroy() {
        releaseDelegate()
        super.onDestroy()
    }

    private fun releaseDelegate() {
        val d = delegate ?: return
        delegate = null
        try {
            d.cancel()
            d.destroy()
        } catch (_: RuntimeException) {
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (_: RemoteException) {
        } catch (_: RuntimeException) {
        }
    }
}
