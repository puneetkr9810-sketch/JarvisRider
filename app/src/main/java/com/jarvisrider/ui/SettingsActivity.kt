package com.jarvisrider.ui

import android.app.Activity
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import com.jarvisrider.R
import com.jarvisrider.core.Prefs

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val ui = Ui(this)

        ui.header(getString(R.string.set_language))
        val group = RadioGroup(this)
        val options = listOf(
            Prefs.LANG_AUTO to getString(R.string.lang_auto),
            Prefs.LANG_EN to getString(R.string.lang_en),
            Prefs.LANG_HI to getString(R.string.lang_hi)
        )
        for ((index, option) in options.withIndex()) {
            val rb = RadioButton(this)
            rb.id = 1000 + index
            rb.text = option.second
            rb.textSize = 16f
            rb.minHeight = ui.dp(48)
            group.addView(rb)
            if (prefs.languageMode == option.first) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            options.getOrNull(checkedId - 1000)?.let { prefs.languageMode = it.first }
        }
        ui.add(group)

        ui.header(getString(R.string.set_audio))
        ui.switch(getString(R.string.set_sco), prefs.useBluetoothSco) { prefs.useBluetoothSco = it }
        ui.switch(getString(R.string.set_offline), prefs.preferOffline) { prefs.preferOffline = it }

        ui.header(getString(R.string.set_incoming))
        ui.switch(getString(R.string.set_announce), prefs.announceIncoming) { prefs.announceIncoming = it }
        ui.switch(getString(R.string.set_listen), prefs.listenIncoming) { prefs.listenIncoming = it }
        ui.switch(getString(R.string.set_mute_ringer), prefs.muteRinger) { prefs.muteRinger = it }
        ui.text(getString(R.string.set_incoming_note), 13f)

        ui.header(getString(R.string.set_wake))
        ui.text(getString(R.string.set_wake_note), 13f)
        ui.attach()
    }
}
