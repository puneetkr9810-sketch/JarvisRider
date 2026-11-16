package com.jarvisrider.core

import android.content.Context
import android.content.SharedPreferences

/** Tiny SharedPreferences wrapper. Nothing is cached in memory beyond what Android itself keeps. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis_rider", Context.MODE_PRIVATE)

    var languageMode: String
        get() = sp.getString(KEY_LANG, LANG_AUTO) ?: LANG_AUTO
        set(v) = sp.edit().putString(KEY_LANG, v).apply()

    var useBluetoothSco: Boolean
        get() = sp.getBoolean("bt_sco", true)
        set(v) = sp.edit().putBoolean("bt_sco", v).apply()

    var announceIncoming: Boolean
        get() = sp.getBoolean("announce_incoming", true)
        set(v) = sp.edit().putBoolean("announce_incoming", v).apply()

    var listenIncoming: Boolean
        get() = sp.getBoolean("listen_incoming", true)
        set(v) = sp.edit().putBoolean("listen_incoming", v).apply()

    var muteRinger: Boolean
        get() = sp.getBoolean("mute_ringer", true)
        set(v) = sp.edit().putBoolean("mute_ringer", v).apply()

    var preferOffline: Boolean
        get() = sp.getBoolean("prefer_offline", false)
        set(v) = sp.edit().putBoolean("prefer_offline", v).apply()

    var lastDialedNumber: String?
        get() = sp.getString("last_number", null)
        set(v) = sp.edit().putString("last_number", v).apply()

    /** Recognition language for a given attempt (0-based). Auto = en-IN first, hi-IN as fallback. */
    fun recognitionLanguage(attempt: Int): String = when (languageMode) {
        LANG_HI -> "hi-IN"
        LANG_EN -> "en-IN"
        else -> if (attempt % 2 == 0) "en-IN" else "hi-IN"
    }

    val promptsHindi: Boolean get() = languageMode == LANG_HI

    companion object {
        private const val KEY_LANG = "language_mode"
        const val LANG_AUTO = "auto"
        const val LANG_EN = "en-IN"
        const val LANG_HI = "hi-IN"
    }
}
