package com.kazumaproject.markdownhelperkeyboard

import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.fever.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeverSoundInstrumentedTest {
    @Test(timeout = 15000) fun generatedSamplesDecodeAndStartOnTheMediaStreamWithoutRecordingPermission() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val audio = context.getSystemService(AudioManager::class.java)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        lateinit var feedback: FeverFeedback
        fun field(name: String): Any? = feedback.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(feedback)
        ins.runOnMainSync { feedback = FeverFeedback(context) }
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            var ready = 0
            while (ready < 3 && SystemClock.uptimeMillis() < deadline) {
                ins.runOnMainSync { ready = (field("ready") as Set<*>).size }
                SystemClock.sleep(20)
            }
            assertEquals("All generated PCM files must decode", 3, ready)
            ins.runOnMainSync {
                assertTrue("Temporary wave files must be deleted after loading", (field("pendingFiles") as Map<*, *>).isEmpty())
                feedback.playEvent(FeverEvent(10, FeverAward.JACKPOT), FeverSettings(sound = true, vibration = false, volume = 100), false)
                assertTrue("SoundPool must return a live stream", (field("streams") as List<*>).any { it is Int && it > 0 })
            }
            // The public playback list anonymizes attributes without privileged audio access.
            // Inspect AudioService through the test shell instead of adding app permissions.
            SystemClock.sleep(220)
            val report = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                ins.uiAutomation.executeShellCommand("dumpsys audio")).bufferedReader().use { it.readText() }
            val player = report.lineSequence().lastOrNull {
                it.contains("uid/pid:${android.os.Process.myUid()}/${android.os.Process.myPid()}") && it.contains("USAGE_GAME")
            }
            assertNotNull("AudioService must register our media sound pool", player)
            val id = Regex("piid:([0-9]+)").find(requireNotNull(player))!!.groupValues[1]
            assertTrue("AudioService must receive playback start", report.contains("player piid:$id event:started"))
            println("FEVER synthesized audio PASS: 3 decoded samples, live media stream, no RECORD_AUDIO grant")
        } finally { ins.runOnMainSync { feedback.release() } }
    }
}
