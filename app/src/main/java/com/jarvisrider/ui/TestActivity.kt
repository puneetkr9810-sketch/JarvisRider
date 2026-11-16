package com.jarvisrider.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import com.jarvisrider.R
import com.jarvisrider.call.TtsAnnouncer
import com.jarvisrider.contacts.ContactsRepo
import com.jarvisrider.core.AnswerParser
import com.jarvisrider.core.CommandParser
import com.jarvisrider.core.NameMatcher
import com.jarvisrider.core.Perms
import com.jarvisrider.core.Prefs
import com.jarvisrider.core.Prompts
import com.jarvisrider.voice.AudioSession
import com.jarvisrider.voice.Heard
import com.jarvisrider.voice.Listener
import com.jarvisrider.voice.RecognizerPicker
import com.jarvisrider.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Diagnostics: TTS, speech recognition + parsing, contact matching, incoming announcement. */
class TestActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        ui.header(getString(R.string.test_title))
        ui.button(getString(R.string.test_tts)) { run { testTts() } }
        ui.button(getString(R.string.test_voice)) { run { testVoice() } }
        val input = EditText(this).apply {
            hint = getString(R.string.test_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        }
        ui.add(input)
        ui.button(getString(R.string.test_match)) { run { testMatch(input.text.toString()) } }
        ui.button(getString(R.string.test_incoming)) {
            show(getString(R.string.test_incoming_running))
            TtsAnnouncer.announce(this, "9876543210") { }
        }
        ui.button(getString(R.string.test_full)) { startActivity(Intent(this, AssistActivity::class.java)) }
        output = ui.text(getString(R.string.test_output_idle))
        ui.attach()
    }

    private fun run(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch { block() }
    }

    private fun show(text: String) {
        output.text = text
    }

    private suspend fun testTts() {
        val prefs = Prefs(this)
        val speaker = Speaker(this)
        val audio = AudioSession(this, prefs.useBluetoothSco)
        try {
            show(getString(R.string.test_tts_running))
            audio.begin()
            if (!speaker.init(prefs.promptsHindi, audio.ttsUsage)) {
                show(getString(R.string.test_tts_failed))
                return
            }
            val ok = speaker.say(Prompts(speaker.hindiActive).ttsTest)
            show(getString(if (ok) R.string.test_tts_ok else R.string.test_tts_failed) +
                "\nHindi voice: " + speaker.hindiActive + "\nBluetooth SCO: " + audio.scoActive)
        } finally {
            speaker.shutdown()
            audio.end()
        }
    }

    private suspend fun testVoice() {
        if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) {
            show(getString(R.string.test_need_mic))
            return
        }
        val prefs = Prefs(this)
        val audio = AudioSession(this, prefs.useBluetoothSco)
        try {
            audio.begin()
            audio.beep()
            val lang = prefs.recognitionLanguage(0)
            show(getString(R.string.test_voice_running) + " (" + lang + ", engine: " +
                (RecognizerPicker.pick(this)?.packageName ?: "default") + ")")
            when (val heard = Listener(this).listen(lang, prefs.preferOffline)) {
                is Heard.Text -> {
                    val sb = StringBuilder()
                    for (alt in heard.alternatives) {
                        sb.append("\u201C").append(alt).append("\u201D\n")
                        sb.append("   command: ").append(CommandParser.parse(alt)).append('\n')
                        sb.append("   answer/reject: ").append(AnswerParser.parse(alt).intent).append('\n')
                    }
                    show(sb.toString())
                }
                is Heard.Failed -> show(getString(R.string.test_voice_error, heard.error))
            }
        } finally {
            audio.end()
        }
    }

    private suspend fun testMatch(name: String) {
        if (name.isBlank()) {
            show(getString(R.string.test_name_hint))
            return
        }
        if (!Perms.has(this, Manifest.permission.READ_CONTACTS)) {
            show(getString(R.string.test_need_contacts))
            return
        }
        val text = withContext(Dispatchers.Default) {
            val contacts = ContactsRepo.loadDialable(this@TestActivity)
            val query = CommandParser.cleanName(name).ifBlank { name }
            val top = contacts.map { it to NameMatcher.score(query, it.name) }.sortedByDescending { it.second }.take(5)
            val result = NameMatcher.match(listOf(query), contacts) { it.name }
            buildString {
                append("Contacts loaded: ").append(contacts.size).append('\n')
                append("Decision: ").append(result::class.java.simpleName).append("\n\n")
                for ((c, s) in top) append(String.format(Locale.US, "%.2f  %s", s, c.name)).append('\n')
            }
        }
        show(text)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
