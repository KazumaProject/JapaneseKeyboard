package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeverFeedbackTest {
    @Test fun generatedPcmHasAValidHeaderAudibleEnergyAndNoClipping() {
        for (sample in FeverSoundSample.entries) {
            val bytes = ByteArrayOutputStream().also(sample::writeWave).toByteArray()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(bytes.size - 8, buffer.getInt(4))
            assertEquals(22050, buffer.getInt(24))
            assertEquals(bytes.size - 44, buffer.getInt(40))
            val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val values = ShortArray(pcm.remaining()).also(pcm::get)
            assertTrue(values.maxOf { kotlin.math.abs(it.toInt()) } < Short.MAX_VALUE)
            assertTrue(values.sumOf { it.toDouble() * it } / values.size > 1000000)
            assertEquals(0, values.first().toInt())
        }
    }

    @Test fun dedicatedSoundAndVolumeWorkWithOrdinaryKeySoundsDisabledAndRespectMute() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("key_sound_preference", false).putInt("key_sound_volume_percent_preference", 1).commit()
        val audio = context.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val feedback = FeverFeedback(context)
        val pool = field(feedback, "pool") as SoundPool
        val shadow = shadowOf(pool)
        @Suppress("UNCHECKED_CAST")
        val paths = (field(feedback, "pendingFiles") as Map<Int, File>).values.map(File::getPath)
        assertEquals(3, paths.size)
        paths.forEach { assertTrue(File(it).isFile); shadow.notifyPathLoaded(it, true) }
        assertTrue(paths.none { File(it).exists() })
        val tick = paths.first()
        try {
            feedback.playEvent(FeverEvent(1), FeverSettings(volume = 100), false)
            assertEquals(.6f, shadow.getPathPlaybacks(tick).last().leftVolume, .001f)
            feedback.playEvent(FeverEvent(1), FeverSettings(volume = 20), false)
            assertEquals(.12f, shadow.getPathPlaybacks(tick).last().leftVolume, .001f)
            shadow.clearPlayed()
            feedback.playEvent(FeverEvent(1), FeverSettings(sound = false), false)
            feedback.playEvent(FeverEvent(1), FeverSettings(volume = 0), false)
            for (mode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
                audio.ringerMode = mode
                feedback.playEvent(FeverEvent(1), FeverSettings(volume = 100), false)
            }
            assertFalse(shadow.wasPathPlayed(tick))
            val attributes = SoundPool::class.java.getDeclaredField("mAttributes").apply { isAccessible = true }.get(pool) as AudioAttributes
            assertEquals(AudioAttributes.USAGE_GAME, attributes.usage)
        } finally { feedback.release() }
    }

    @Test fun releasingBeforeDecodeDeletesTemporarySamples() {
        val feedback = FeverFeedback(ApplicationProvider.getApplicationContext())
        @Suppress("UNCHECKED_CAST")
        val files = (field(feedback, "pendingFiles") as Map<Int, File>).values.toList()
        assertEquals(3, files.size)
        feedback.release()
        assertTrue(files.none(File::exists))
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
