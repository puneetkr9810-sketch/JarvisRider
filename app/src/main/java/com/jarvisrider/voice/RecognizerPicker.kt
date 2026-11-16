package com.jarvisrider.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService

/**
 * Chooses the speech-recognition engine to use.
 *
 * When JarvisRider becomes the default assistant, Android may also make JarvisRider's
 * own (proxy) RecognitionService the system default. We must never bind to ourselves,
 * so we always pick a real engine explicitly: the previous system default if it is not
 * us, otherwise Google, otherwise any other installed engine.
 */
object RecognizerPicker {

    private val preferredPackages = listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.tts",
        "com.google.android.as",
        "com.samsung.android.bixby.agent"
    )

    fun pick(context: Context): ComponentName? {
        val own = context.packageName
        val systemDefault = try {
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
                ?.let { ComponentName.unflattenFromString(it) }
        } catch (_: RuntimeException) {
            null
        }
        val installed = queryServices(context).mapNotNull { it.serviceInfo }
            .filter { it.packageName != own && it.exported }
            .map { ComponentName(it.packageName, it.name) }
        if (systemDefault != null && systemDefault.packageName != own && systemDefault in installed) {
            return systemDefault
        }
        return installed.minByOrNull { c ->
            val i = preferredPackages.indexOf(c.packageName)
            if (i < 0) Int.MAX_VALUE else i
        }
    }

    fun isAnyAvailable(context: Context): Boolean = pick(context) != null

    @Suppress("DEPRECATION")
    private fun queryServices(context: Context): List<ResolveInfo> = try {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentServices(intent, 0)
        }
    } catch (_: RuntimeException) {
        emptyList()
    }
}
