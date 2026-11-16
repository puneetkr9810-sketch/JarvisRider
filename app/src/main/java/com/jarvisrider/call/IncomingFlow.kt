package com.jarvisrider.call

import android.Manifest
import android.content.Context
import android.os.Build
import com.jarvisrider.contacts.ContactsRepo
import com.jarvisrider.core.AnswerDecision
import com.jarvisrider.core.AnswerIntent
import com.jarvisrider.core.AnswerParser
import com.jarvisrider.core.Perms
import com.jarvisrider.core.Prefs
import com.jarvisrider.core.Prompts
import com.jarvisrider.core.TextNorm
import com.jarvisrider.voice.AudioSession
import com.jarvisrider.voice.Heard
import com.jarvisrider.voice.Listener
import com.jarvisrider.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * "Call from Rahul. Say answer or reject." -> listen ~6 s -> accept / reject.
 * If listening is not allowed ([micAllowed] = false) it only announces the caller.
 */
class IncomingFlow(
    private val context: Context,
    private val number: String?,
    private val micAllowed: Boolean
) {
    suspend fun run() {
        val prefs = Prefs(context)
        val name = withContext(Dispatchers.IO) { ContactsRepo.nameForNumber(context, number) }
        val audio = AudioSession(context, prefs.useBluetoothSco)
        val speaker = Speaker(context)
        val ringer = RingerMuter(context)
        try {
            if (!CallActions.isRinging(context)) return
            if (prefs.muteRinger) ringer.mute()
            audio.begin()
            speaker.init(prefs.promptsHindi, audio.ttsUsage)
            val p = Prompts(speaker.hindiActive)
            val who = name ?: number?.takeIf { it.isNotBlank() }?.let { TextNorm.spokenDigits(it) } ?: p.unknownCaller

            val canListen = micAllowed && prefs.listenIncoming &&
                Perms.has(context, Manifest.permission.RECORD_AUDIO) &&
                Perms.has(context, Manifest.permission.ANSWER_PHONE_CALLS)
            if (!canListen) {
                speaker.say(p.callFrom(who))
                return
            }

            for (attempt in 0 until ATTEMPTS) {
                speaker.say(if (attempt == 0) p.callFromAsk(who) else p.didntCatch + " " + p.sayAnswerOrReject)
                if (!CallActions.isRinging(context)) return
                val heard = Listener(context).listen(prefs.recognitionLanguage(attempt), prefs.preferOffline, LISTEN_MS)
                if (!CallActions.isRinging(context)) return
                if (heard is Heard.Failed &&
                    (Listener.isPermissionError(heard.error) || Listener.isEngineProblem(heard.error))
                ) {
                    return // microphone blocked in background on this OS, or no engine: leave the call ringing
                }
                val decision = (heard as? Heard.Text)?.let { AnswerParser.parseAlternatives(it.alternatives) }
                    ?: AnswerDecision(AnswerIntent.UNKNOWN)
                when (decision.intent) {
                    AnswerIntent.ANSWER -> {
                        speaker.shutdown()
                        audio.end()
                        ringer.restore()
                        if (CallActions.acceptRingingCall(context) && decision.speaker) {
                            delay(1_500)
                            CallActions.enableSpeakerLegacy(context)
                        }
                        return
                    }
                    AnswerIntent.REJECT -> {
                        if (Build.VERSION.SDK_INT < 28) {
                            speaker.say(p.rejectUnsupported)
                        } else if (CallActions.rejectRingingCall(context)) {
                            ringer.restore()
                            speaker.say(p.rejected)
                        }
                        return
                    }
                    AnswerIntent.UNKNOWN -> Unit
                }
            }
        } finally {
            speaker.shutdown()
            audio.end()
            ringer.restore()
        }
    }

    private companion object {
        const val ATTEMPTS = 2
        const val LISTEN_MS = 6_500L
    }
}
