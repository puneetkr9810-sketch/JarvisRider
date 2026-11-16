package com.jarvisrider.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.jarvisrider.R
import com.jarvisrider.call.RingerMuter
import com.jarvisrider.core.Perms
import com.jarvisrider.voice.AudioSession
import com.jarvisrider.voice.RecognizerPicker

/** First-run / home screen: setup checklist with one button per step. */
class MainActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RingerMuter.restoreIfNeeded(this)
        val ui = Ui(this)
        ui.header(getString(R.string.app_name))
        ui.text(getString(R.string.main_intro))
        ui.header(getString(R.string.main_status))
        statusView = ui.text("")
        ui.header(getString(R.string.main_setup))
        ui.button(getString(R.string.btn_grant)) { askPermissions() }
        ui.button(getString(R.string.btn_default_assistant)) {
            Toast.makeText(this, R.string.toast_pick_assistant, Toast.LENGTH_LONG).show()
            if (!SystemSettings.openAssistantSettings(this)) Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show()
        }
        ui.button(getString(R.string.btn_battery)) {
            if (!SystemSettings.openBatteryOptimization(this)) Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show()
        }
        ui.header(getString(R.string.main_use))
        ui.button(getString(R.string.btn_try)) { startActivity(Intent(this, AssistActivity::class.java)) }
        ui.button(getString(R.string.btn_settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }
        ui.button(getString(R.string.btn_test)) { startActivity(Intent(this, TestActivity::class.java)) }
        ui.button(getString(R.string.btn_help)) { startActivity(Intent(this, HelpActivity::class.java)) }
        ui.header(getString(R.string.main_why_perms))
        ui.text(Perms.all().joinToString("\n\n") { "\u2022 " + it.title + ": " + it.why })
        ui.attach()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ok = "\u2705"
        val no = "\u274C"
        val sb = StringBuilder()
        for (item in Perms.all()) {
            sb.append(if (Perms.has(this, item.permission)) ok else no).append(' ').append(item.title).append('\n')
        }
        sb.append('\n')
        sb.append(if (SystemSettings.isDefaultAssistant(this)) ok else no).append(' ').append(getString(R.string.status_assistant)).append('\n')
        sb.append(if (SystemSettings.isIgnoringBatteryOptimizations(this)) ok else no).append(' ').append(getString(R.string.status_battery)).append('\n')
        sb.append(if (RecognizerPicker.isAnyAvailable(this)) ok else no).append(' ').append(getString(R.string.status_engine)).append('\n')
        val headset = AudioSession(this, true).hasBluetoothHeadset()
        sb.append(if (headset) ok else "\u2796").append(' ').append(getString(if (headset) R.string.status_headset_yes else R.string.status_headset_no))
        statusView.text = sb.toString()
    }

    private fun askPermissions() {
        val missing = Perms.missing(this)
        if (missing.isEmpty()) {
            Toast.makeText(this, R.string.toast_all_granted, Toast.LENGTH_SHORT).show()
            return
        }
        val message = missing.joinToString("\n\n") { "\u2022 " + it.title + ": " + it.why }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_perm_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_continue) { _, _ ->
                requestPermissions(missing.map { it.permission }.toTypedArray(), REQ_PERMS)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        refresh()
        val stillMissing = Perms.missing(this)
        // If Android stopped showing the dialog ("Don't ask again"), send the user to App info.
        val blocked = stillMissing.any { !shouldShowRequestPermissionRationale(it.permission) }
        if (stillMissing.isNotEmpty() && blocked) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_perm_blocked_title)
                .setMessage(R.string.dialog_perm_blocked_msg)
                .setPositiveButton(R.string.dialog_open_settings) { _, _ -> SystemSettings.openAppDetails(this) }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private companion object {
        const val REQ_PERMS = 7
    }
}
