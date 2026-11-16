package com.jarvisrider.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Runtime-permission helpers plus the rationale shown to the user. */
object Perms {

    data class Item(val permission: String, val title: String, val why: String)

    fun all(): List<Item> {
        val list = mutableListOf(
            Item(Manifest.permission.RECORD_AUDIO, "Microphone", "Hear your voice commands through the helmet headset."),
            Item(Manifest.permission.READ_CONTACTS, "Contacts", "Find the person you name and announce who is calling."),
            Item(Manifest.permission.CALL_PHONE, "Make calls", "Place the call you ask for without touching the phone."),
            Item(Manifest.permission.ANSWER_PHONE_CALLS, "Answer calls", "Answer or reject an incoming call when you say so."),
            Item(Manifest.permission.READ_PHONE_STATE, "Phone state", "Know when the phone is ringing."),
            Item(Manifest.permission.READ_CALL_LOG, "Call log", "Get the caller's number on Android 9+ and support redial / call back.")
        )
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(Item(Manifest.permission.BLUETOOTH_CONNECT, "Nearby devices (Bluetooth)", "Detect your helmet headset and route audio to it."))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Item(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "Show the short notification required while an incoming call is announced."))
        }
        return list
    }

    fun has(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context): List<Item> = all().filterNot { has(context, it.permission) }
}
