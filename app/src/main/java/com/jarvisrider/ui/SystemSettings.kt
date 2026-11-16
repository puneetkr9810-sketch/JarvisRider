package com.jarvisrider.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/** Deep links into system settings, with fallbacks for OEM differences. */
object SystemSettings {

    fun isDefaultAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            return try {
                context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
            } catch (_: RuntimeException) {
                false
            }
        }
        return try {
            val value = Settings.Secure.getString(context.contentResolver, "assistant")
                ?: Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            value?.startsWith(context.packageName + "/") == true
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Opens the screen where the "Digital assistant app" is chosen. */
    fun openAssistantSettings(activity: Activity): Boolean = tryStart(
        activity,
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (_: RuntimeException) {
        false
    }

    /**
     * Battery-optimisation exemption is essential for this app: it is what lets Android start the
     * short incoming-call service from the background on Android 12+.
     */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimization(activity: Activity): Boolean = tryStart(
        activity,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.packageName)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetailsIntent(activity)
    )

    fun openAppDetails(activity: Activity): Boolean = tryStart(activity, appDetailsIntent(activity))

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))

    /** OEM "autostart" / background-launch managers. Falls back to App info. */
    fun openAutostartSettings(activity: Activity): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val candidates = mutableListOf<Intent>()
        fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> {
                candidates += comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
            "oppo" in m || "realme" in m || "oneplus" in m -> {
                candidates += comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                candidates += comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                candidates += comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                candidates += comp("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity")
            }
            "vivo" in m || "iqoo" in m -> {
                candidates += comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                candidates += comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            }
            "samsung" in m -> {
                candidates += comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
                candidates += comp("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
            }
            "huawei" in m || "honor" in m -> {
                candidates += comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            }
        }
        candidates += appDetailsIntent(activity)
        return tryStart(activity, *candidates.toTypedArray())
    }

    private fun tryStart(activity: Activity, vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: RuntimeException) {
            }
        }
        return false
    }
}
