package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import com.kazumaproject.markdownhelperkeyboard.R

internal class FeverFeedback(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val streams = ArrayList<Int>(2)
    private val handler = Handler(Looper.getMainLooper())
    private val accents = mutableListOf<Runnable>()
    private val tick: Int
    private val chime: Int
    private val jackpot: Int
    init {
        pool.setOnLoadCompleteListener { _, sample, status -> if (status == 0) ready.add(sample) }
        tick = pool.load(context, R.raw.fever_tick, 1)
        chime = pool.load(context, R.raw.fever_chime, 1)
        jackpot = pool.load(context, R.raw.fever_jackpot, 1)
    }
    fun play(award: FeverAward, settings: FeverSettings, soundAllowed: Boolean, vibrationAllowed: Boolean, globalVolume: Int) =
        playEvent(FeverEvent(10, award), settings, soundAllowed, vibrationAllowed, globalVolume)

    fun playEvent(event: FeverEvent, settings: FeverSettings, soundAllowed: Boolean, vibrationAllowed: Boolean, globalVolume: Int) {
        val volume = settings.volume / 100f * (if (globalVolume == 0) 1f else globalVolume.coerceIn(0, 100) / 100f)
        fun sound(sample: Int, gain: Float, pitch: Float) {
            if (sample !in ready || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            if (streams.size >= 3) pool.stop(streams.removeAt(0))
            streams.add(pool.play(sample, volume * gain, volume * gain, 1, 0, pitch))
        }
        if (settings.sound && soundAllowed && volume > 0) {
            val pitch = when (event.cue) {
                FeverCue.DELETE -> .8f
                FeverCue.CURSOR_LEFT, FeverCue.CURSOR_RIGHT, FeverCue.CURSOR_UP, FeverCue.CURSOR_DOWN -> 1.7f
                else -> 1f + (event.combo % 10) * .055f
            }
            sound(tick, .28f, pitch)
            if (event.award != null) {
                accents.forEach(handler::removeCallbacks); accents.clear()
                // Match the visual gather, release, and sparkle rather than playing one flat bell.
                listOf(160L, 460L).forEachIndexed { index, delay ->
                    val accent = Runnable { sound(if (index == 0 && event.award == FeverAward.JACKPOT) jackpot else chime,
                        if (index == 0) .9f else .38f, if (index == 0) 1f else 1.3f) }
                    accents.add(accent); handler.postDelayed(accent, delay)
                }
            }
        }
        if (settings.vibration && vibrationAllowed && audio.ringerMode != AudioManager.RINGER_MODE_SILENT && vibrator?.hasVibrator() == true) {
            if (event.award != null) {
                val timings = longArrayOf(0, 8, 145, 22, 280, 12)
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(timings, intArrayOf(0, 70, 0, 180, 0, 100), -1))
                else @Suppress("DEPRECATION") vibrator.vibrate(timings, -1)
            } else if (event.cue == null) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(5, 55))
                else @Suppress("DEPRECATION") vibrator.vibrate(5)
            }
        }
    }
    fun stop() {
        accents.forEach(handler::removeCallbacks); accents.clear()
        streams.forEach(pool::stop); streams.clear(); vibrator?.cancel()
    }
    fun release() { stop(); pool.release() }
}
