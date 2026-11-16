package com.jarvisrider.smoke

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.speech.RecognitionService
import android.telephony.TelephonyManager
import org.robolectric.RuntimeEnvironment
import com.jarvisrider.call.IncomingCallReceiver
import com.jarvisrider.call.IncomingCallService
import com.jarvisrider.ui.AssistActivity
import com.jarvisrider.ui.HelpActivity
import com.jarvisrider.ui.MainActivity
import com.jarvisrider.ui.SettingsActivity
import com.jarvisrider.ui.TestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * JVM smoke tests (Robolectric, Android 14 framework): every screen starts without crashing,
 * the manifest exposes the assistant entry points, and the call receiver behaves.
 * They do not replace a test on a real phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSmokeTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    @Test fun mainActivityStartsWithoutPermissions() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(c.get().isFinishing)
        c.pause().stop().destroy()
    }

    @Test fun secondaryScreensStart() {
        for (cls in listOf(SettingsActivity::class.java, TestActivity::class.java, HelpActivity::class.java)) {
            val c = Robolectric.buildActivity(cls).setup()
            assertFalse(cls.simpleName, c.get().isFinishing)
            c.pause().stop().destroy()
        }
    }

    @Test fun assistActivityStartsAndCanBeDestroyedMidConversation() {
        val c = Robolectric.buildActivity(AssistActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        c.pause().stop().destroy() // must cancel the conversation without crashing
    }

    @Test fun assistIntentResolvesToJarvis() {
        val pm = app.packageManager
        val assist = pm.queryIntentActivities(Intent(Intent.ACTION_ASSIST), 0)
        assertTrue(assist.any { it.activityInfo.name == AssistActivity::class.java.name })
        val voiceCommand = pm.queryIntentActivities(Intent(Intent.ACTION_VOICE_COMMAND), 0)
        assertTrue(voiceCommand.any { it.activityInfo.name == AssistActivity::class.java.name })
    }

    @Test fun voiceInteractionServiceIsDeclaredAndProtected() {
        val pm = app.packageManager
        val info = pm.getServiceInfo(
            ComponentName(app, "com.jarvisrider.assist.JarvisVoiceInteractionService"), PackageManager.GET_META_DATA
        )
        assertEquals(Manifest.permission.BIND_VOICE_INTERACTION, info.permission)
        assertTrue(info.metaData?.containsKey("android.voice_interaction") == true)
        val session = pm.getServiceInfo(ComponentName(app, "com.jarvisrider.assist.JarvisSessionService"), 0)
        assertEquals(Manifest.permission.BIND_VOICE_INTERACTION, session.permission)
    }

    @Test fun recognitionServiceDeclared() {
        val list = app.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        assertTrue(list.any { it.serviceInfo.name == "com.jarvisrider.assist.ProxyRecognitionService" })
    }

    @Test fun incomingServiceIsNotExported() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, IncomingCallService::class.java), 0)
        assertFalse(info.exported)
    }

    private fun ringing(number: String?) = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
        putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
        @Suppress("DEPRECATION")
        if (number != null) putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number)
    }

    @Test fun receiverIgnoresRingWithoutPermissions() {
        IncomingCallReceiver().onReceive(app, ringing("9876543210"))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun receiverStartsServiceWhenRinging() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
        val tm = app.getSystemService(TelephonyManager::class.java)
        shadowOf(tm).setCallState(TelephonyManager.CALL_STATE_RINGING)
        // The copy without the number is skipped when READ_CALL_LOG is granted.
        IncomingCallReceiver().onReceive(app, ringing(null))
        assertNull(shadowOf(app).nextStartedService)
        IncomingCallReceiver().onReceive(app, ringing("9876543210"))
        val started = shadowOf(app).nextStartedService
        assertNotNull(started)
        assertEquals(IncomingCallService::class.java.name, started.component?.className)
        assertEquals("9876543210", started.getStringExtra(IncomingCallService.EXTRA_NUMBER))
    }

    @Test fun receiverIgnoresIdleAndSpoofedRings() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val idle = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            .putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        IncomingCallReceiver().onReceive(app, idle)
        assertNull(shadowOf(app).nextStartedService)
        // "RINGING" broadcast while the phone is actually idle is ignored.
        IncomingCallReceiver().onReceive(app, ringing("123"))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun incomingServiceRunsAndStopsWhenNotRinging() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildService(IncomingCallService::class.java,
            Intent(app, IncomingCallService::class.java).putExtra(IncomingCallService.EXTRA_NUMBER, "9876543210"))
        try {
            controller.create().startCommand(0, 1)
            // The flow hops to Dispatchers.IO for the contact lookup; pump the main looper until it ends.
            val deadline = System.currentTimeMillis() + 10_000
            while (!shadowOf(controller.get()).isStoppedBySelf && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            // Phone state is IDLE in the test, so the flow must end and the service stop itself.
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
        assertFalse(IncomingCallService.active)
    }
}
