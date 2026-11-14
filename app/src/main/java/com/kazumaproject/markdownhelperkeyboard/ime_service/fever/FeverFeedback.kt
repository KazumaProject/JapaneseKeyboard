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
import java.io.File

internal class FeverFeedback(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val pendingFiles = mutableMapOf<Int, File>()
    private var released = false
    private val streams = ArrayList<Int>(2)
    private val handler = Handler(Looper.getMainLooper())
    private val accents = mutableListOf<Runnable>()
    private val tick: Int
    private val chime: Int
    private val jackpot: Int
    init {
        pool.setOnLoadCompleteListener { _, sample, status ->
            pendingFiles.remove(sample)?.delete()
            if (!released && status == 0) ready.add(sample)
        }
        fun load(sample: FeverSoundSample): Int {
            // SoundPool decodes files asynchronously. Keep only a private temporary WAV until
            // decoding completes, then retain the decoded sample in memory for this session.
            val file = try { File.createTempFile("fever-", ".wav", context.cacheDir) }
                catch (_: java.io.IOException) { return 0 }
            return try {
                file.outputStream().buffered().use(sample::writeWave)
                pool.load(file.path, 1).also { id ->
                    if (id == 0) file.delete() else pendingFiles[id] = file
                }
            } catch (_: java.io.IOException) { file.delete(); 0 }
        }
        tick = load(FeverSoundSample.TICK)
        chime = load(FeverSoundSample.CHIME)
        jackpot = load(FeverSoundSample.JACKPOT)
    }
    fun play(award: FeverAward, settings: FeverSettings, vibrationAllowed: Boolean) =
        playEvent(FeverEvent(10, award), settings, vibrationAllowed)

    fun playEvent(event: FeverEvent, settings: FeverSettings, vibrationAllowed: Boolean) {
        val volume = settings.volume.coerceIn(0, 100) / 100f
        fun sound(sample: Int, gain: Float, pitch: Float) {
            if (released || sample !in ready || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            if (streams.size >= 3) pool.stop(streams.removeAt(0))
            streams.add(pool.play(sample, volume * gain, volume * gain, 1, 0, pitch))
        }
        if (settings.sound && volume > 0) {
            val pitch = when (event.cue) {
                FeverCue.DELETE -> .8f
                FeverCue.CURSOR_LEFT, FeverCue.CURSOR_RIGHT, FeverCue.CURSOR_UP, FeverCue.CURSOR_DOWN -> 1.7f
                else -> 1f + (event.combo % 10) * .055f
            }
            sound(tick, .6f, pitch)
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
    fun release() {
        released = true; stop(); pool.release(); ready.clear()
        pendingFiles.values.forEach(File::delete); pendingFiles.clear()
    }
}
