package com.jarvisrider.voice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import com.jarvisrider.core.Perms
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Audio plumbing for one interaction:
 *  - transient audio focus (music pauses / ducks while we talk and listen),
 *  - optional Bluetooth SCO routing so prompts play in the helmet and the helmet mic is used,
 *  - a short beep.
 * Always call [end] when the interaction finishes.
 */
class AudioSession(context: Context, private val wantSco: Boolean) {
    private val ctx = context.applicationContext
    private val am: AudioManager? = ctx.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var savedMode: Int? = null
    private var commDeviceSet = false
    private var legacyScoStarted = false

    var scoActive = false
        private set

    val ttsUsage: Int
        get() = if (scoActive) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANT

    private val toneStream: Int
        get() = if (scoActive) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC

    suspend fun begin(allowSco: Boolean = true) {
        requestFocus()
        if (wantSco && allowSco && hasBluetoothHeadset()) {
            scoActive = try {
                startSco()
            } catch (_: RuntimeException) {
                false
            }
            if (!scoActive) stopSco()
        }
    }

    fun hasBluetoothHeadset(): Boolean {
        val manager = am ?: return false
        val viaAudio = try {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        } catch (_: RuntimeException) {
            false
        }
        if (viaAudio) return true
        if (Build.VERSION.SDK_INT >= 31 && !Perms.has(ctx, Manifest.permission.BLUETOOTH_CONNECT)) return false
        return try {
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            adapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private suspend fun startSco(): Boolean {
        val manager = am ?: return false
        if (manager.mode == AudioManager.MODE_NORMAL) {
            savedMode = manager.mode
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        return if (Build.VERSION.SDK_INT >= 31) {
            val device = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?: return false
            if (!manager.setCommunicationDevice(device)) return false
            commDeviceSet = true
            repeat(30) {
                if (manager.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    delay(SETTLE_MS) // let the SCO link come up before we speak
                    return true
                }
                delay(100)
            }
            false
        } else {
            startLegacySco(manager)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun startLegacySco(manager: AudioManager): Boolean {
        val connected = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) connected.complete(true)
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }
        try {
            manager.startBluetoothSco()
            manager.isBluetoothScoOn = true
            legacyScoStarted = true
            val ok = withTimeoutOrNull(3_000L) { connected.await() } ?: false
            if (ok) delay(SETTLE_MS)
            return ok
        } finally {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopSco() {
        val manager = am ?: return
        try {
            if (commDeviceSet && Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
            if (legacyScoStarted) {
                manager.isBluetoothScoOn = false
                manager.stopBluetoothSco()
            }
            savedMode?.let { manager.mode = it }
        } catch (_: RuntimeException) {
        }
        commDeviceSet = false
        legacyScoStarted = false
        savedMode = null
        scoActive = false
    }

    private fun requestFocus() {
        val manager = am ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
        try {
            manager.requestAudioFocus(req)
            focusRequest = req
        } catch (_: RuntimeException) {
        }
    }

    suspend fun beep() {
        var tone: ToneGenerator? = null
        try {
            tone = ToneGenerator(toneStream, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            delay(250)
        } catch (_: RuntimeException) {
            // ToneGenerator can fail when the audio HAL is busy; the beep is optional.
        } finally {
            tone?.release()
        }
    }

    /** Releases SCO routing and audio focus. Safe to call more than once. */
    fun end() {
        stopSco()
        val manager = am ?: return
        focusRequest?.let {
            try {
                manager.abandonAudioFocusRequest(it)
            } catch (_: RuntimeException) {
            }
        }
        focusRequest = null
    }

    private companion object {
        const val SETTLE_MS = 600L
    }
}
