package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.media.GemmaPcmWavWriter
import com.google.android.material.color.MaterialColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Recording belongs to one input session. Hiding the keyboard discards it and any late result. */
class AiVoiceInputController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val manager: AiVoiceInputManager,
    private val sessionId: () -> Long,
    private val onResult: (String) -> Unit,
    private val onBusy: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val status = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 18f
        setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
    }
    private val stop = Button(context).apply { setText(R.string.voice_input_stop); setOnClickListener { stopRecording() } }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE))
        isClickable = true
        addView(status, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(stop)
            addView(Button(context).apply { setText(android.R.string.cancel); setOnClickListener { cancel() } })
        })
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        isVisible = false
    }
    @Volatile private var recording = false
    @Volatile private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var revision = 0L
    val isActive: Boolean get() = job?.isActive == true

    fun attachTo(host: ViewGroup) {
        (panel.parent as? ViewGroup)?.removeView(panel)
        host.addView(panel, FrameLayout.LayoutParams(-1, -1))
    }

    @SuppressLint("MissingPermission")
    fun start(settings: VoiceInputSettings, language: String) {
        if (isActive) { stopRecording(); return }
        if (settings.modelPath.isBlank() || !File(settings.modelPath).isFile) {
            onMessage(context.getString(R.string.voice_input_model_missing))
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onMessage(context.getString(R.string.gemma_audio_permission_settings_hint))
            return
        }
        val token = ++revision
        val inputSession = sessionId()
        val directory = File(context.cacheDir, "voice-input").apply { mkdirs() }
        val raw = File(directory, "${UUID.randomUUID()}.pcm")
        val wav = File(directory, "${UUID.randomUUID()}.wav")
        recording = true
        panel.isVisible = true
        ViewCompat.requestApplyInsets(panel)
        status.setText(R.string.voice_input_recording)
        stop.isEnabled = true
        onBusy(true)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(bufferSize > 0) { context.getString(R.string.gemma_audio_unavailable) }
                    val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize.coerceAtLeast(4096))
                    try {
                        check(audio.state == AudioRecord.STATE_INITIALIZED) { context.getString(R.string.gemma_audio_unavailable) }
                        recorder = audio
                        val started = SystemClock.elapsedRealtime()
                        if (recording && token == revision) audio.startRecording()
                        raw.outputStream().buffered().use { output ->
                            val buffer = ByteArray(bufferSize.coerceAtLeast(4096))
                            while (isActive && recording && SystemClock.elapsedRealtime() - started < 30000L) {
                                val count = audio.read(buffer, 0, buffer.size)
                                if (count > 0) output.write(buffer, 0, count)
                                else if (recording) error(context.getString(R.string.gemma_audio_unavailable))
                            }
                        }
                    } finally {
                        runCatching { audio.stop() }
                        audio.release()
                        if (recorder === audio) recorder = null
                    }
                    check(raw.length() > 0) { context.getString(R.string.voice_input_no_audio) }
                    GemmaPcmWavWriter.writeMono16Bit(raw, wav, 16000)
                }
                recording = false
                stop.isEnabled = false
                status.setText(R.string.voice_input_transcribing)
                val result = manager.transcribe(wav.absolutePath, language, settings)
                if (token == revision && inputSession == sessionId()) onResult(settings.transform(result, language))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == revision) onMessage(error.localizedMessage ?: context.getString(R.string.gemma_audio_unavailable))
            } finally {
                raw.delete()
                wav.delete()
                if (token == revision) {
                    recording = false
                    panel.isVisible = false
                    onBusy(false)
                }
            }
        }
    }

    private fun stopRecording() {
        recording = false
        runCatching { recorder?.stop() }
    }

    fun cancel() {
        revision++
        stopRecording()
        manager.cancel()
        job?.cancel()
        job = null
        panel.isVisible = false
        onBusy(false)
    }

    fun destroy() { cancel(); (panel.parent as? ViewGroup)?.removeView(panel) }
}
