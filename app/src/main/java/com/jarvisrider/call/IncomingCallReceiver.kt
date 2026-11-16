package com.jarvisrider.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.jarvisrider.core.Perms
import com.jarvisrider.core.Prefs

/**
 * Manifest-registered PHONE_STATE receiver: no service runs while idle.
 * PHONE_STATE is one of the implicit broadcasts still delivered to manifest receivers.
 * The receiver is protected by READ_PHONE_STATE, so only senders holding that permission
 * (the telephony stack) can trigger it, and the ringing state is re-checked before acting.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            RingerMuter.restoreIfNeeded(context)
            return
        }
        val prefs = Prefs(context)
        if (!prefs.announceIncoming) return
        if (!Perms.has(context, Manifest.permission.READ_PHONE_STATE)) return

        // With READ_CALL_LOG Android sends RINGING twice: once without and once with the number.
        // Only act on the copy that carries the number in that case.
        val hasCallLog = Perms.has(context, Manifest.permission.READ_CALL_LOG)
        @Suppress("DEPRECATION")
        val numberKey = TelephonyManager.EXTRA_INCOMING_NUMBER
        if (hasCallLog && !intent.hasExtra(numberKey)) return
        val number = intent.getStringExtra(numberKey)

        if (IncomingCallService.active) return
        if (!CallActions.isRinging(context)) return

        val service = Intent(context, IncomingCallService::class.java)
            .putExtra(IncomingCallService.EXTRA_NUMBER, number)
        try {
            context.startForegroundService(service)
        } catch (_: IllegalStateException) {
            // Android 12+: ForegroundServiceStartNotAllowedException (a subclass) when the app may not
            // start a foreground service from the background. Fall back to a TTS-only announcement.
            announceOnly(context, number)
        } catch (_: SecurityException) {
            announceOnly(context, number)
        }
    }

    private fun announceOnly(context: Context, number: String?) {
        val pending = goAsync()
        TtsAnnouncer.announce(context, number) {
            try {
                pending.finish()
            } catch (_: IllegalStateException) {
            }
        }
    }
}
