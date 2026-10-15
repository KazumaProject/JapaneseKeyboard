package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Short PCM tones synthesized locally; no packaged media, network, or recording access. */
internal enum class FeverSoundSample(val seconds: Double) {
    TICK(.065), CHIME(.32), JACKPOT(.65);

    fun writeWave(output: OutputStream) {
        val rate = 22050
        val frames = (rate * seconds).toInt()
        fun little(value: Int, bytes: Int) { repeat(bytes) { output.write(value ushr (8 * it) and 255) } }
        output.write("RIFF".toByteArray(Charsets.US_ASCII)); little(36 + frames * 2, 4)
        output.write("WAVEfmt ".toByteArray(Charsets.US_ASCII)); little(16, 4)
        little(1, 2); little(1, 2); little(rate, 4); little(rate * 2, 4); little(2, 2); little(16, 2)
        output.write("data".toByteArray(Charsets.US_ASCII)); little(frames * 2, 4)
        repeat(frames) { index ->
            val t = index.toDouble() / rate
            val attack = (t / .004).coerceAtMost(1.0)
            val release = ((seconds - t) / .025).coerceIn(0.0, 1.0)
            val tone = when (this) {
                TICK -> (sin(2 * PI * 1320 * t) + .35 * sin(2 * PI * 1980 * t)) / 1.35 * exp(-t * 32)
                CHIME -> (sin(2 * PI * 1046.5 * t) + sin(2 * PI * 1318.5 * t) + sin(2 * PI * 1568 * t)) / 3 * exp(-t * 7)
                JACKPOT -> {
                    val note = when ((t / .12).toInt()) { 0 -> 784; 1 -> 988; 2 -> 1175; else -> 1568 }
                    (sin(2 * PI * note * t) + .3 * sin(4 * PI * note * t)) / 1.3 * exp(-t * 1.8)
                }
            }
            little((tone * attack * release * .8 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767), 2)
        }
    }
}
