package com.jarvisrider.assist

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.LayoutInflater
import android.view.View
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
 * The assistant overlay shown on power-button / home long-press. Runs one [Conversation]
 * and finishes itself; all resources are released when it ends or is hidden.
 */
class JarvisSession(context: Context) : VoiceInteractionSession(context), Conversation.Host {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var statusView: TextView? = null

    @SuppressLint("InflateParams") // the session window supplies the parent; there is no root to attach to
    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.assist_overlay, null)
        statusView = view.findViewById(R.id.status)
        view.setOnClickListener { finish() }
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (job?.isActive == true) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        job = s.launch { Conversation(context, this@JarvisSession).run() }
    }

    override fun onHide() {
        super.onHide()
        stop()
    }

    override fun onDestroy() {
        stop()
        statusView = null
        super.onDestroy()
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        job = null
    }

    override fun showStatus(text: String) {
        statusView?.text = text
    }

    override fun onConversationFinished() {
        mainHandler.post {
            try {
                finish()
            } catch (_: RuntimeException) {
            }
        }
    }
}
