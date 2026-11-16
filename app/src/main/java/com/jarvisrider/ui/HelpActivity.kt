package com.jarvisrider.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.jarvisrider.R

class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        ui.header(getString(R.string.help_launch_title))
        ui.text(getString(R.string.help_launch))
        ui.button(getString(R.string.btn_default_assistant)) { notFound(SystemSettings.openAssistantSettings(this)) }

        ui.header(getString(R.string.help_battery_title))
        ui.text(getString(R.string.help_battery))
        ui.button(getString(R.string.btn_battery)) { notFound(SystemSettings.openBatteryOptimization(this)) }

        ui.header(getString(R.string.help_oem_title))
        ui.text(getString(R.string.help_oem))
        ui.button(getString(R.string.btn_autostart)) { notFound(SystemSettings.openAutostartSettings(this)) }
        ui.button(getString(R.string.btn_app_info)) { notFound(SystemSettings.openAppDetails(this)) }

        ui.header(getString(R.string.help_voice_title))
        ui.text(getString(R.string.help_voice))

        ui.header(getString(R.string.help_trouble_title))
        ui.text(getString(R.string.help_trouble))
        ui.attach()
    }

    private fun notFound(ok: Boolean) {
        if (!ok) Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show()
    }
}
