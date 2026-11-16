package com.jarvisrider.voice

import kotlinx.coroutines.CancellableContinuation
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Resumes a continuation at most once, from any thread (TTS/recognizer callbacks race each other). */
internal class Once<T>(private val cont: CancellableContinuation<T>) {
    private val done = AtomicBoolean(false)
    fun resume(value: T) {
        if (done.compareAndSet(false, true) && cont.isActive) cont.resume(value)
    }
}
