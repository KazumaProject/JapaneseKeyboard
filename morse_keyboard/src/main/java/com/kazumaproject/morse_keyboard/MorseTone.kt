package com.kazumaproject.morse_keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/** PCM is synthesized in memory; no recordings or sound assets are used. */
internal class MorseTone(private val context: Context) {
    @Volatile private var sounding = false
    @Volatile private var settings = MorseSettings()
    private var playback: AtomicBoolean? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleRelease = Runnable { if (!sounding) release() }
    private var worker: java.util.concurrent.ExecutorService? = null

    fun set(on: Boolean, configuration: MorseSettings) {
        settings = configuration
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sounding = on && configuration.sound && manager.ringerMode == AudioManager.RINGER_MODE_NORMAL
        handler.removeCallbacks(idleRelease)
        if (!sounding) {
            if (playback != null) handler.postDelayed(idleRelease, 1000)
            return
        }
        if (playback?.get() == true) return
        val alive = AtomicBoolean(true)
        playback = alive
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "MorsePCM").apply { isDaemon = true } }
        worker = executor
        executor.execute {
            var track: AudioTrack? = null
            try {
                val rate = 48000
                val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(minimum.coerceAtLeast(1920))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                if (Build.VERSION.SDK_INT >= 26) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                track = builder.build()
                track.play()
                val samples = ShortArray(240)
                val oscillator = MorseOscillator(rate)
                while (alive.get()) {
                    val config = settings
                    val target = if (sounding) config.volumePercent.coerceIn(0, 100) / 100.0 else 0.0
                    oscillator.fill(samples, config.frequencyHz.coerceIn(200, 1500), target)
                    if (track.write(samples, 0, samples.size) < 0) break
                }
            } catch (_: Exception) {
                // Audio initialization/output is optional; decoding remains fully functional.
            } finally {
                runCatching { track?.pause() }
                runCatching { track?.flush() }
                runCatching { track?.release() }
                alive.set(false)
                executor.shutdown()
            }
        }
    }

    fun release() {
        handler.removeCallbacks(idleRelease)
        sounding = false
        playback?.set(false)
        playback = null
        worker?.shutdown()
        worker = null
    }
}

/** Envelope changes over a few milliseconds to avoid clicks at key-down and key-up. */
internal class MorseOscillator(private val sampleRate: Int) {
    private var phase = 0.0
    private var gain = 0.0
    fun fill(samples: ShortArray, frequency: Int, targetGain: Double) {
        for (i in samples.indices) {
            gain += (targetGain - gain).coerceIn(-0.003, 0.003)
            samples[i] = (sin(phase) * gain * Short.MAX_VALUE).toInt().toShort()
            phase = (phase + 2 * PI * frequency / sampleRate) % (2 * PI)
        }
    }
}
