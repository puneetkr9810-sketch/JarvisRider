package com.jarvisrider.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.jarvisrider.core.Perms
import com.jarvisrider.core.Prefs

/** Placing, answering and rejecting calls. Every method checks its permission and never throws. */
object CallActions {

    @SuppressLint("MissingPermission") // checked via Perms.has right above each call
    fun placeCall(context: Context, number: String, speaker: Boolean): Boolean {
        if (!Perms.has(context, Manifest.permission.CALL_PHONE)) return false
        val uri = Uri.fromParts("tel", number, null)
        val extras = Bundle().apply { putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, speaker) }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val placed = try {
            if (telecom != null) {
                telecom.placeCall(uri, extras)
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        val ok = placed || try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, uri)
                    .putExtra(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, speaker)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: RuntimeException) {
            false
        }
        if (ok) Prefs(context).lastDialedNumber = number
        return ok
    }

    /** Last outgoing number (redial). Falls back to the last number JarvisRider dialled. */
    fun lastOutgoingNumber(context: Context): String? {
        if (Perms.has(context, Manifest.permission.READ_CALL_LOG)) {
            try {
                val n = CallLog.Calls.getLastOutgoingCall(context)
                if (!n.isNullOrBlank()) return n
            } catch (_: RuntimeException) {
            }
        }
        return Prefs(context).lastDialedNumber
    }

    /** Most recent call in either direction (for "call back"). */
    fun lastCallNumber(context: Context): String? {
        if (!Perms.has(context, Manifest.permission.READ_CALL_LOG)) return lastOutgoingNumber(context)
        val args = Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, 5)
        }
        return try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), args, null)?.use { c ->
                var found: String? = null
                while (found == null && c.moveToNext()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) found = n
                }
                found
            }
        } catch (_: RuntimeException) {
            null
        } ?: lastOutgoingNumber(context)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun isRinging(context: Context): Boolean {
        if (!Perms.has(context, Manifest.permission.READ_PHONE_STATE)) return true // can't tell; assume yes
        return try {
            context.getSystemService(TelephonyManager::class.java)?.callState == TelephonyManager.CALL_STATE_RINGING
        } catch (_: RuntimeException) {
            true
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun acceptRingingCall(context: Context): Boolean {
        if (!Perms.has(context, Manifest.permission.ANSWER_PHONE_CALLS)) return false
        return try {
            context.getSystemService(TelecomManager::class.java)?.acceptRingingCall()
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Rejects the ringing call. Only possible on Android 9+ (TelecomManager.endCall). */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun rejectRingingCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (!Perms.has(context, Manifest.permission.ANSWER_PHONE_CALLS)) return false
        return try {
            context.getSystemService(TelecomManager::class.java)?.endCall() ?: false
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Best-effort speakerphone after answering an incoming call. Only attempted below Android 12:
     * on 12+ a third-party app cannot reliably change the routing of a telephony call.
     */
    @Suppress("DEPRECATION")
    fun enableSpeakerLegacy(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return false
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return try {
            am.isSpeakerphoneOn = true
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
