package com.jarvisrider.call

import android.content.Context
import android.media.AudioManager

/**
 * Temporarily mutes the ringtone so the announcement is audible and the recognizer is not
 * flooded by the ringtone. The "muted by us" flag is persisted so the ringer is restored even if
 * the process dies mid-call (restored on the next PHONE_STATE broadcast or app start).
 */
class RingerMuter(context: Context) {
    private val ctx = context.applicationContext
    private val am: AudioManager? = ctx.getSystemService(AudioManager::class.java)

    fun mute() {
        val manager = am ?: return
        try {
            if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            if (manager.getStreamVolume(AudioManager.STREAM_RING) == 0) return
            manager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            setFlag(ctx, true)
        } catch (_: SecurityException) {
            // Do Not Disturb policy forbids it; announce over the ringtone instead.
        } catch (_: RuntimeException) {
        }
    }

    fun restore() = restoreIfNeeded(ctx)

    companion object {
        private const val KEY = "ringer_muted_by_us"

        private fun setFlag(context: Context, value: Boolean) {
            context.getSharedPreferences("jarvis_rider", Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        }

        fun restoreIfNeeded(context: Context) {
            val app = context.applicationContext
            val sp = app.getSharedPreferences("jarvis_rider", Context.MODE_PRIVATE)
            if (!sp.getBoolean(KEY, false)) return
            try {
                app.getSystemService(AudioManager::class.java)
                    ?.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            } catch (_: RuntimeException) {
            }
            setFlag(app, false)
        }

    }
}
