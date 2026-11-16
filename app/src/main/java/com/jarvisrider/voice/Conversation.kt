package com.jarvisrider.voice

import android.Manifest
import android.content.Context
import com.jarvisrider.call.CallActions
import com.jarvisrider.contacts.ContactEntry
import com.jarvisrider.contacts.ContactsRepo
import com.jarvisrider.core.Command
import com.jarvisrider.core.CommandParser
import com.jarvisrider.core.MatchResult
import com.jarvisrider.core.NameMatcher
import com.jarvisrider.core.OptionPicker
import com.jarvisrider.core.Perms
import com.jarvisrider.core.Prefs
import com.jarvisrider.core.Prompts
import com.jarvisrider.core.Scored
import com.jarvisrider.core.TextNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The eyes-free outgoing-call dialogue:
 * beep -> "Who do you want to call?" -> listen -> match contact -> "Calling X" -> place call.
 * Also handles one-shot commands ("call Rahul on speaker", "redial", "Rahul ko call karo").
 *
 * Every resource (TTS, recognizer, SCO, audio focus) is created inside [run] and released in its
 * finally block, so nothing survives the interaction.
 */
class Conversation(private val context: Context, private val host: Host) {

    interface Host {
        fun showStatus(text: String)
        fun onConversationFinished()
    }

    private sealed class Outcome {
        object Done : Outcome()
        data class Retry(val message: String?) : Outcome()
        data class AskWho(val speaker: Boolean) : Outcome()
    }

    private val prefs = Prefs(context)
    private var prompts = Prompts(false)
    private lateinit var audio: AudioSession
    private lateinit var speaker: Speaker
    private val listener = Listener(context)

    suspend fun run() {
        audio = AudioSession(context, prefs.useBluetoothSco)
        speaker = Speaker(context)
        try {
            audio.begin()
            speaker.init(prefs.promptsHindi, audio.ttsUsage)
            prompts = Prompts(speaker.hindiActive)
            if (!Perms.has(context, Manifest.permission.RECORD_AUDIO)) {
                tell(prompts.micMissing)
                return
            }
            audio.beep()
            dialogue()
        } finally {
            speaker.shutdown()
            audio.end()
            host.onConversationFinished()
        }
    }

    private suspend fun tell(text: String) {
        host.showStatus(text)
        speaker.say(text)
    }

    private suspend fun dialogue() {
        var speakerMode = false
        var question = prompts.whoToCall
        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            tell(question)
            when (val heard = listener.listen(prefs.recognitionLanguage(attempt), prefs.preferOffline)) {
                is Heard.Failed -> when {
                    Listener.isPermissionError(heard.error) -> {
                        tell(prompts.micMissing); return
                    }
                    Listener.isEngineProblem(heard.error) -> {
                        tell(prompts.recognizerUnavailable); return
                    }
                    else -> question = prompts.didntCatch + " " + prompts.tryAgainWho
                }
                is Heard.Text -> {
                    host.showStatus("\u201C" + heard.alternatives.first() + "\u201D")
                    when (val outcome = handle(heard.alternatives, speakerMode)) {
                        Outcome.Done -> return
                        is Outcome.AskWho -> {
                            speakerMode = outcome.speaker
                            question = prompts.whoToCall
                        }
                        is Outcome.Retry -> question = (outcome.message ?: prompts.didntCatch) + " " + prompts.tryAgainWho
                    }
                }
            }
            attempt++
        }
        tell(prompts.goodbye)
    }

    private suspend fun handle(alternatives: List<String>, speakerMode: Boolean): Outcome {
        val commands = alternatives.map { CommandParser.parse(it) }
        val command = commands.firstOrNull { it !is Command.Unknown } ?: Command.Unknown(alternatives.first())
        return when (command) {
            Command.Cancel -> {
                tell(prompts.cancelled); Outcome.Done
            }
            is Command.AskWho -> Outcome.AskWho(command.speaker || speakerMode)
            is Command.Redial -> {
                val number = withContext(Dispatchers.IO) {
                    if (command.lastAny) CallActions.lastCallNumber(context) else CallActions.lastOutgoingNumber(context)
                }
                if (number == null) {
                    tell(prompts.noRedial); Outcome.Done
                } else {
                    val name = withContext(Dispatchers.IO) { ContactsRepo.nameForNumber(context, number) }
                    dial(name ?: TextNorm.spokenDigits(number), number, command.speaker || speakerMode)
                }
            }
            is Command.CallNumber -> dial(TextNorm.spokenDigits(command.number), command.number, command.speaker || speakerMode)
            is Command.Call, is Command.Unknown -> {
                val queries = if (command is Command.Call) {
                    commands.filterIsInstance<Command.Call>().map { it.name }
                } else {
                    alternatives.map { CommandParser.cleanName(it) }
                }
                val wantSpeaker = ((command as? Command.Call)?.speaker ?: false) || speakerMode
                matchAndDial(queries, wantSpeaker)
            }
        }
    }

    private suspend fun matchAndDial(queries: List<String>, wantSpeaker: Boolean): Outcome {
        if (!Perms.has(context, Manifest.permission.READ_CONTACTS)) {
            tell(prompts.contactsMissing)
            return Outcome.Done
        }
        val result = withContext(Dispatchers.Default) {
            val contacts = ContactsRepo.loadDialable(context)
            NameMatcher.match(queries, contacts) { it.name }
        }
        return when (result) {
            is MatchResult.Single -> dial(result.item.name, result.item.number, wantSpeaker)
            is MatchResult.Ambiguous -> askWhich(result.options, wantSpeaker)
            is MatchResult.NoMatch -> {
                val spoken = queries.firstOrNull { it.isNotBlank() }
                // A clear-but-unknown name gets a specific message; garbage gets "didn't catch that".
                Outcome.Retry(if (spoken != null && result.bestScore > 0.7) prompts.notFound(spoken) else null)
            }
        }
    }

    private suspend fun askWhich(options: List<Scored<ContactEntry>>, wantSpeaker: Boolean): Outcome {
        val names = options.map { it.item.name }
        var question = prompts.whichOne(names)
        repeat(WHICH_ATTEMPTS) { attempt ->
            tell(question)
            when (val heard = listener.listen(prefs.recognitionLanguage(attempt), prefs.preferOffline)) {
                is Heard.Text -> {
                    if (heard.alternatives.any { CommandParser.parse(it) == Command.Cancel }) {
                        tell(prompts.cancelled)
                        return Outcome.Done
                    }
                    val index = heard.alternatives.firstNotNullOfOrNull { OptionPicker.pick(it, names) }
                    if (index != null) {
                        val chosen = options[index].item
                        return dial(chosen.name, chosen.number, wantSpeaker)
                    }
                }
                is Heard.Failed -> {
                    if (Listener.isPermissionError(heard.error) || Listener.isEngineProblem(heard.error)) {
                        tell(if (Listener.isPermissionError(heard.error)) prompts.micMissing else prompts.recognizerUnavailable)
                        return Outcome.Done
                    }
                }
            }
            question = prompts.didntCatch + " " + prompts.whichOne(names)
        }
        tell(prompts.goodbye)
        return Outcome.Done
    }

    private suspend fun dial(spokenName: String, number: String, wantSpeaker: Boolean): Outcome {
        if (!Perms.has(context, Manifest.permission.CALL_PHONE)) {
            tell(prompts.callMissing)
            return Outcome.Done
        }
        tell(prompts.calling(spokenName, wantSpeaker))
        audio.end() // hand Bluetooth/audio focus back before the dialer takes over
        if (!CallActions.placeCall(context, number, wantSpeaker)) tell(prompts.callFailed)
        return Outcome.Done
    }

    private companion object {
        const val MAX_ATTEMPTS = 3 // first try + up to two retries
        const val WHICH_ATTEMPTS = 2
    }
}
