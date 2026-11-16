package com.jarvisrider.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import com.jarvisrider.R
import com.jarvisrider.voice.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Transparent fallback entry point for ACTION_ASSIST, ACTION_VOICE_COMMAND (Bluetooth headset
 * button / HFP voice dial) and VOICE_SEARCH_HANDS_FREE. Works over the lock screen.
 */
class AssistActivity : Activity(), Conversation.Host {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var status: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.assist_overlay)
        status = findViewById(R.id.status)
        findViewById<android.view.View>(R.id.overlay_root).setOnClickListener { finish() }
        start()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        start()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch { Conversation(this@AssistActivity, this@AssistActivity).run() }
    }

    override fun showStatus(text: String) {
        status?.text = text
    }

    override fun onConversationFinished() {
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        scope.cancel()
        status = null
        super.onDestroy()
    }
}
