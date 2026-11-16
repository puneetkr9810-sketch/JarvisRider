package com.jarvisrider.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionService

/**
 * Registers JarvisRider as a "Digital assistant app". While JarvisRider is the default assistant,
 * Android keeps this (empty) service bound; it holds no resources and does no work until the
 * assistant is invoked (power-button / home long-press / headset), which opens [JarvisSession].
 */
class JarvisVoiceInteractionService : VoiceInteractionService() {
    override fun onLaunchVoiceAssistFromKeyguard() {
        try {
            showSession(Bundle(), 0)
        } catch (_: RuntimeException) {
            // Service not ready yet (e.g. right after boot); nothing to do.
        }
    }
}
