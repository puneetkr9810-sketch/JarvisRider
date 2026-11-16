package com.jarvisrider.call

import android.content.Context
import android.media.AudioAttributes
import com.jarvisrider.contacts.ContactsRepo
import com.jarvisrider.core.Prefs
import com.jarvisrider.core.Prompts
import com.jarvisrider.core.TextNorm
import com.jarvisrider.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fallback used when Android does not let us start the foreground service from the background:
 * announce the caller by TTS only (no listening). Bounded to ~9 s so it fits inside a
 * BroadcastReceiver.goAsync() window.
 */
object TtsAnnouncer {

    fun announce(context: Context, number: String?, onDone: () -> Unit) {
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            try {
                withTimeoutOrNull(9_000L) {
                    val name = withContext(Dispatchers.IO) { ContactsRepo.nameForNumber(app, number) }
                    val speaker = Speaker(app)
                    try {
                        if (speaker.init(Prefs(app).promptsHindi, AudioAttributes.USAGE_ASSISTANT)) {
                            val p = Prompts(speaker.hindiActive)
                            val who = name ?: number?.takeIf { it.isNotBlank() }?.let { TextNorm.spokenDigits(it) } ?: p.unknownCaller
                            speaker.say(p.callFrom(who))
                        }
                    } finally {
                        speaker.shutdown()
                    }
                }
            } finally {
                onDone()
                scope.cancel()
            }
        }
    }
}
