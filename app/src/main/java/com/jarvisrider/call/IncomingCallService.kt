package com.jarvisrider.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import com.jarvisrider.R
import com.jarvisrider.core.Perms
import com.jarvisrider.core.Prefs
import com.jarvisrider.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that exists only while a call is ringing (~10-20 s).
 * It is required on Android 10+ to use the microphone from the background.
 * Type "microphone" is tried first; if Android refuses it (background while-in-use rules),
 * type "phoneCall" is used and the caller is only announced.
 */
class IncomingCallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var stateReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val micAllowed = goForeground()
        if (micAllowed == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job != null) return START_NOT_STICKY // already announcing this call
        active = true
        registerStateReceiver()
        val number = intent?.getStringExtra(EXTRA_NUMBER)
        job = scope.launch {
            try {
                IncomingFlow(this@IncomingCallService, number, micAllowed).run()
            } finally {
                stopSelfNow()
            }
        }
        return START_NOT_STICKY
    }

    /** @return true = foreground with microphone, false = foreground without mic, null = failed. */
    private fun goForeground(): Boolean? {
        val notification = buildNotification()
        val wantMic = Prefs(this).listenIncoming && Perms.has(this, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 30) {
            if (wantMic) {
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    return true
                } catch (_: RuntimeException) {
                    // SecurityException / ForegroundServiceStartNotAllowedException: retry without mic
                }
            }
            return try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                false
            } catch (_: RuntimeException) {
                null
            }
        }
        if (Build.VERSION.SDK_INT == 29) {
            return try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                wantMic // Android 10 has no microphone FGS type; a foreground service may record
            } catch (_: RuntimeException) {
                null
            }
        }
        return try {
            startForeground(NOTIFICATION_ID, notification)
            wantMic
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(getString(R.string.notif_incoming_title))
            .setContentText(getString(R.string.notif_incoming_text))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    /** Stops the dialogue as soon as the call is answered/ended by any other means. */
    private fun registerStateReceiver() {
        if (stateReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val state = i?.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (state != null && state != TelephonyManager.EXTRA_STATE_RINGING) job?.cancel()
            }
        }
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
            else registerReceiver(r, filter)
            stateReceiver = r
        } catch (_: RuntimeException) {
        }
    }

    private fun stopSelfNow() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: RuntimeException) {
        }
        stopSelf()
    }

    override fun onDestroy() {
        stateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        stateReceiver = null
        scope.cancel()
        RingerMuter.restoreIfNeeded(this)
        active = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        private const val CHANNEL_ID = "incoming_call"
        private const val NOTIFICATION_ID = 42

        @Volatile
        var active = false
            private set
    }
}
