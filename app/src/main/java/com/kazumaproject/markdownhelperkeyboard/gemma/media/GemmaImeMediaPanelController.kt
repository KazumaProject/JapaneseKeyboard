package com.kazumaproject.markdownhelperkeyboard.gemma.media

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ViewGemmaImeMediaPanelBinding
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaPromptBuilder
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.output
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.repository.GemmaPromptTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the Gemma image/audio workflow displayed inside the IME window.
 *
 * Model inference remains in the crash-isolated `:gemma` process. This controller only owns the
 * keyboard surface, temporary media, recording, and explicit result insertion.
 */
class GemmaImeMediaPanelController(
    private val context: Context,
    private val actionRepository: GemmaPromptTemplateRepository,
    private val gemmaManager: GemmaTranslationManager,
    private val appPreference: AppPreference,
    private val clipboardUtil: ClipboardUtil,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onVisibilityChanged(visible: Boolean)
        fun currentInputSessionId(): Long
        fun insertResult(text: String, inputSessionId: Long): Boolean
        fun onDeviceImageRequested()
        fun onAudioPermissionRequired()
        fun showMessage(message: String)
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binding = ViewGemmaImeMediaPanelBinding.inflate(LayoutInflater.from(context))
    private var state = GemmaImeMediaState.Hidden
    private var actions: List<GemmaPromptTemplate> = emptyList()
    private var inputSessionIdAtOpen = -1L
    private var actionLoadJob: Job? = null
    private var mediaLoadJob: Job? = null
    private var mediaLoadRequestId = 0L
    private var inferenceJob: Job? = null
    private var recordingJob: Job? = null
    private var recordingTickerJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val recording = AtomicBoolean(false)
    private var recordingStartedAt = 0L
    private var recordedDurationSeconds = 0L

    val isShowing: Boolean
        get() = state.visible

    init {
        binding.root.isVisible = false
        bindListeners()
        render()
    }

    fun attachTo(host: ViewGroup) {
        (binding.root.parent as? ViewGroup)?.removeView(binding.root)
        host.addView(
            binding.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        binding.root.isVisible = state.visible
    }

    fun openImage() {
        open(GemmaInputModality.IMAGE)
        loadActions(GemmaInputModality.IMAGE)
        loadClipboardImage()
    }

    fun openPickedImage(path: String) {
        open(GemmaInputModality.IMAGE)
        loadActions(GemmaInputModality.IMAGE)
        loadDeviceImage(path)
    }

    fun openAudio() {
        open(GemmaInputModality.AUDIO)
        loadActions(GemmaInputModality.AUDIO)
        state = state.copy(
            phase = GemmaImeMediaPhase.SELECTING,
            status = context.getString(R.string.gemma_audio_ready_to_record),
        )
        render()
    }

    fun handleBack(): Boolean {
        if (!isShowing) return false
        when (state.phase) {
            GemmaImeMediaPhase.RESULT,
            GemmaImeMediaPhase.ERROR -> {
                state = state.copy(
                    phase = if (state.hasMedia) {
                        GemmaImeMediaPhase.READY
                    } else {
                        GemmaImeMediaPhase.SELECTING
                    },
                    result = "",
                    candidates = emptyList(),
                    error = null,
                )
                render()
            }

            else -> close()
        }
        return true
    }

    fun onInputViewHidden() {
        if (isShowing) close()
    }

    fun onInputSessionChanged() {
        if (isShowing) close()
    }

    fun close() {
        if (!state.visible) return
        cancelActiveWork()
        deleteOwnedMedia(state.mediaPath)
        state = GemmaImeMediaState.Hidden
        binding.root.isVisible = false
        callbacks.onVisibilityChanged(false)
        render()
    }

    fun destroy() {
        cancelActiveWork()
        deleteOwnedMedia(state.mediaPath)
        state = GemmaImeMediaState.Hidden
        (binding.root.parent as? ViewGroup)?.removeView(binding.root)
        controllerScope.cancel()
    }

    private fun open(modality: GemmaInputModality) {
        cancelActiveWork()
        deleteOwnedMedia(state.mediaPath)
        inputSessionIdAtOpen = callbacks.currentInputSessionId()
        actions = emptyList()
        binding.spinnerAction.adapter = spinnerAdapter(emptyList())
        recordingStartedAt = 0L
        recordedDurationSeconds = 0L
        state = GemmaImeMediaState(
            visible = true,
            modality = modality,
            phase = GemmaImeMediaPhase.SELECTING,
            status = context.getString(R.string.gemma_media_loading_actions),
        )
        binding.root.isVisible = true
        callbacks.onVisibilityChanged(true)
        render()
    }

    private fun bindListeners() = with(binding) {
        buttonClose.setOnClickListener { close() }
        buttonClipboardImage.setOnClickListener { loadClipboardImage() }
        buttonDeviceImage.setOnClickListener {
            close()
            callbacks.onDeviceImageRequested()
        }
        buttonRecord.setOnClickListener { ensureAudioPermissionAndRecord() }
        buttonStopRecording.setOnClickListener { stopRecording() }
        buttonRun.setOnClickListener { runSelectedAction() }
        buttonCancelRun.setOnClickListener {
            cancelInference()
            state = state.copy(
                phase = if (state.hasMedia) {
                    GemmaImeMediaPhase.READY
                } else {
                    GemmaImeMediaPhase.SELECTING
                },
                status = if (state.hasMedia) {
                    currentMediaReadyStatus()
                } else {
                    state.status
                },
                error = null,
            )
            render()
        }
        buttonInsertResult.setOnClickListener {
            val selectedResult = selectedResult()
            if (selectedResult.isBlank()) return@setOnClickListener
            if (callbacks.insertResult(selectedResult, inputSessionIdAtOpen)) {
                close()
            } else {
                state = state.copy(
                    phase = GemmaImeMediaPhase.ERROR,
                    error = context.getString(R.string.gemma_input_session_changed),
                )
                render()
            }
        }
        buttonCopyResult.setOnClickListener {
            val selectedResult = selectedResult()
            if (selectedResult.isBlank()) return@setOnClickListener
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Gemma", selectedResult))
            callbacks.showMessage(context.getString(R.string.gemma_result_copied))
        }
        buttonRetry.setOnClickListener {
            state = state.copy(
                phase = if (state.hasMedia) {
                    GemmaImeMediaPhase.READY
                } else {
                    GemmaImeMediaPhase.SELECTING
                },
                result = "",
                candidates = emptyList(),
                error = null,
                status = if (state.hasMedia) currentMediaReadyStatus() else state.status,
            )
            render()
        }
    }

    private fun loadActions(modality: GemmaInputModality) {
        actionLoadJob?.cancel()
        actionLoadJob = controllerScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    actionRepository.getEnabledActions(modality)
                }
            }
            if (!state.visible || state.modality != modality) return@launch
            loaded.onSuccess {
                actions = it
                binding.spinnerAction.adapter = spinnerAdapter(actions.map { action -> action.title })
                binding.emptyActions.isVisible = actions.isEmpty()
                if (state.status == context.getString(R.string.gemma_media_loading_actions)) {
                    state = state.copy(
                        status = if (modality == GemmaInputModality.IMAGE) {
                            context.getString(R.string.gemma_image_loading_clipboard)
                        } else {
                            context.getString(R.string.gemma_audio_ready_to_record)
                        },
                    )
                }
            }.onFailure {
                state = state.copy(
                    phase = GemmaImeMediaPhase.ERROR,
                    error = it.localizedMessage
                        ?: context.getString(R.string.gemma_media_action_failed),
                )
            }
            render()
        }
    }

    private fun loadClipboardImage() {
        if (!state.visible || state.modality != GemmaInputModality.IMAGE) return
        val requestId = beginImageSelection(
            context.getString(R.string.gemma_image_loading_clipboard),
        )
        mediaLoadJob = controllerScope.launch {
            val item = clipboardUtil.getPrimaryClipContent() as? ClipboardItem.Image
            if (item == null) {
                if (isCurrentImageLoad(requestId)) {
                    state = state.copy(
                        phase = GemmaImeMediaPhase.SELECTING,
                        mediaPath = null,
                        imageSource = null,
                        status = context.getString(R.string.gemma_clipboard_image_unavailable),
                    )
                    render()
                }
                return@launch
            }

            var target: File? = null
            val file = try {
                withContext(Dispatchers.IO) {
                    target = File(
                        mediaCacheDirectory(),
                        "clipboard_${System.currentTimeMillis()}.png",
                    )
                    target!!.outputStream().buffered().use { output ->
                        check(item.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    target!!
                }
            } catch (error: CancellationException) {
                target?.delete()
                throw error
            } catch (error: Throwable) {
                target?.delete()
                if (isCurrentImageLoad(requestId)) {
                    state = state.copy(
                        phase = GemmaImeMediaPhase.ERROR,
                        error = error.localizedMessage
                            ?: context.getString(R.string.gemma_media_import_failed),
                    )
                    render()
                }
                return@launch
            }

            if (!isCurrentImageLoad(requestId)) {
                file.delete()
                return@launch
            }
            state = state.copy(
                phase = GemmaImeMediaPhase.READY,
                mediaPath = file.absolutePath,
                imageSource = GemmaImeImageSource.CLIPBOARD,
                status = context.getString(R.string.gemma_image_source_clipboard),
                error = null,
            )
            binding.imagePreview.setImageURI(Uri.fromFile(file))
            render()
        }
    }

    private fun loadDeviceImage(path: String) {
        if (!state.visible || state.modality != GemmaInputModality.IMAGE) return
        val requestId = beginImageSelection(
            context.getString(R.string.gemma_image_source_device),
        )
        mediaLoadJob = controllerScope.launch {
            val file = withContext(Dispatchers.IO) {
                val selected = File(path)
                val directory = runCatching { mediaCacheDirectory().canonicalFile }.getOrNull()
                val candidate = runCatching { selected.canonicalFile }.getOrNull()
                candidate?.takeIf {
                    directory != null &&
                        it.parentFile == directory &&
                        it.isFile &&
                        it.length() > 0L
                }
            }
            if (!isCurrentImageLoad(requestId)) {
                deleteOwnedMedia(path)
                return@launch
            }
            if (file == null) {
                deleteOwnedMedia(path)
                state = state.copy(
                    phase = GemmaImeMediaPhase.ERROR,
                    mediaPath = null,
                    imageSource = null,
                    error = context.getString(R.string.gemma_device_image_import_failed),
                )
                render()
                return@launch
            }
            state = state.copy(
                phase = GemmaImeMediaPhase.READY,
                mediaPath = file.absolutePath,
                imageSource = GemmaImeImageSource.DEVICE,
                status = context.getString(R.string.gemma_image_source_device),
                error = null,
            )
            binding.imagePreview.setImageURI(Uri.fromFile(file))
            render()
        }
    }

    private fun beginImageSelection(status: String): Long {
        mediaLoadJob?.cancel()
        mediaLoadJob = null
        mediaLoadRequestId += 1L
        deleteOwnedMedia(state.mediaPath)
        binding.imagePreview.setImageDrawable(null)
        state = state.copy(
            phase = GemmaImeMediaPhase.SELECTING,
            mediaPath = null,
            imageSource = null,
            status = status,
            error = null,
            result = "",
            candidates = emptyList(),
        )
        render()
        return mediaLoadRequestId
    }

    private fun isCurrentImageLoad(requestId: Long): Boolean {
        return requestId == mediaLoadRequestId &&
            state.visible &&
            state.modality == GemmaInputModality.IMAGE
    }

    private fun ensureAudioPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            state = state.copy(
                phase = GemmaImeMediaPhase.ERROR,
                error = context.getString(R.string.gemma_audio_permission_denied),
            )
            render()
            callbacks.onAudioPermissionRequired()
        }
    }

    private fun startRecording() {
        if (recording.getAndSet(true)) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            recording.set(false)
            showRecordingError(context.getString(R.string.gemma_audio_unavailable))
            return
        }
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer.coerceAtLeast(MIN_AUDIO_BUFFER),
            )
        }.getOrElse {
            recording.set(false)
            showRecordingError(
                it.localizedMessage ?: context.getString(R.string.gemma_audio_unavailable),
            )
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            recording.set(false)
            showRecordingError(context.getString(R.string.gemma_audio_unavailable))
            return
        }

        deleteOwnedMedia(state.mediaPath)
        val timestamp = System.currentTimeMillis()
        val rawFile = File(mediaCacheDirectory(), "audio_$timestamp.pcm")
        val wavFile = File(mediaCacheDirectory(), "audio_$timestamp.wav")
        audioRecord = recorder
        recordingStartedAt = SystemClock.elapsedRealtime()
        recordedDurationSeconds = 0L
        state = state.copy(
            phase = GemmaImeMediaPhase.RECORDING,
            mediaPath = null,
            status = context.getString(R.string.gemma_audio_recording_seconds, 0L),
            result = "",
            candidates = emptyList(),
            error = null,
        )
        render()

        runCatching { recorder.startRecording() }.onFailure {
            recorder.release()
            audioRecord = null
            recording.set(false)
            showRecordingError(
                it.localizedMessage ?: context.getString(R.string.gemma_audio_unavailable),
            )
            return
        }

        recordingJob = controllerScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBuffer.coerceAtLeast(MIN_AUDIO_BUFFER))
            runCatching {
                FileOutputStream(rawFile).use { output ->
                    while (isActive && recording.get() &&
                        SystemClock.elapsedRealtime() - recordingStartedAt < MAX_RECORDING_MS
                    ) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) output.write(buffer, 0, read)
                    }
                }
            }
            recording.set(false)
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            recorder.release()
            audioRecord = null

            val finalized = runCatching {
                if (rawFile.length() <= 0L) error("No audio was recorded.")
                GemmaPcmWavWriter.writeMono16Bit(rawFile, wavFile, SAMPLE_RATE)
                wavFile
            }
            val finalDurationSeconds = elapsedRecordingSeconds()
            rawFile.delete()
            withContext(Dispatchers.Main.immediate) {
                if (!state.visible || state.modality != GemmaInputModality.AUDIO) {
                    wavFile.delete()
                    return@withContext
                }
                finalized.onSuccess {
                    recordedDurationSeconds = finalDurationSeconds
                    state = state.copy(
                        phase = GemmaImeMediaPhase.READY,
                        mediaPath = it.absolutePath,
                        status = context.getString(
                            R.string.gemma_audio_recorded,
                            recordedDurationSeconds,
                        ),
                        error = null,
                    )
                }.onFailure {
                    wavFile.delete()
                    showRecordingError(
                        it.localizedMessage
                            ?: context.getString(R.string.gemma_audio_unavailable),
                    )
                }
                render()
            }
        }
        recordingTickerJob = controllerScope.launch {
            while (recording.get()) {
                state = state.copy(
                    status = context.getString(
                        R.string.gemma_audio_recording_seconds,
                        elapsedRecordingSeconds(),
                    ),
                )
                render()
                delay(250L)
            }
        }
    }

    private fun stopRecording() {
        if (!recording.getAndSet(false)) return
        runCatching { audioRecord?.stop() }
    }

    private fun showRecordingError(message: String) {
        state = state.copy(
            phase = GemmaImeMediaPhase.ERROR,
            mediaPath = null,
            error = message,
        )
        render()
    }

    private fun runSelectedAction() {
        if (state.phase == GemmaImeMediaPhase.RUNNING) return
        val mediaPath = state.mediaPath ?: return
        val action = actions.getOrNull(binding.spinnerAction.selectedItemPosition) ?: return
        state = state.copy(
            phase = GemmaImeMediaPhase.RUNNING,
            result = "",
            candidates = emptyList(),
            error = null,
            status = context.getString(R.string.gemma_media_processing),
        )
        render()
        inferenceJob?.cancel()
        inferenceJob = controllerScope.launch {
            try {
                val prompt = GemmaPromptBuilder.build(
                    action,
                    appPreference.gemma_translation_target_language_preference,
                )
                val raw = gemmaManager.runMediaPrompt(
                    prompt = prompt,
                    mediaPath = mediaPath,
                    mediaType = if (state.modality == GemmaInputModality.IMAGE) {
                        GemmaMediaType.IMAGE
                    } else {
                        GemmaMediaType.AUDIO
                    },
                )
                if (!state.visible) return@launch
                val candidates = if (action.output() == GemmaOutputMode.CANDIDATE_LIST) {
                    GemmaPromptBuilder.parseCandidates(raw)
                } else {
                    emptyList()
                }
                state = state.copy(
                    phase = GemmaImeMediaPhase.RESULT,
                    result = candidates.firstOrNull() ?: raw.trim(),
                    candidates = candidates,
                    status = context.getString(R.string.gemma_result_ready_in_keyboard),
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!state.visible) return@launch
                state = state.copy(
                    phase = GemmaImeMediaPhase.ERROR,
                    error = error.localizedMessage
                        ?: context.getString(R.string.gemma_media_action_failed),
                )
            } finally {
                inferenceJob = null
                render()
            }
        }
    }

    private fun cancelInference() {
        gemmaManager.cancelActiveTranslation()
        inferenceJob?.cancel()
        inferenceJob = null
    }

    private fun cancelActiveWork() {
        actionLoadJob?.cancel()
        actionLoadJob = null
        mediaLoadJob?.cancel()
        mediaLoadJob = null
        mediaLoadRequestId += 1L
        cancelInference()
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        stopRecording()
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.release()
        audioRecord = null
    }

    private fun render() = with(binding) {
        val visible = state.visible
        root.isVisible = visible
        if (!visible) return

        title.text = context.getString(
            if (state.modality == GemmaInputModality.IMAGE) {
                R.string.gemma_image_action_title
            } else {
                R.string.gemma_audio_action_title
            },
        )
        imageGroup.isVisible =
            state.modality == GemmaInputModality.IMAGE &&
                state.phase != GemmaImeMediaPhase.RESULT
        audioGroup.isVisible =
            state.modality == GemmaInputModality.AUDIO &&
                state.phase != GemmaImeMediaPhase.RESULT
        actionGroup.isVisible = state.phase in setOf(
            GemmaImeMediaPhase.SELECTING,
            GemmaImeMediaPhase.READY,
            GemmaImeMediaPhase.ERROR,
        )
        progressGroup.isVisible = state.phase == GemmaImeMediaPhase.RUNNING
        resultGroup.isVisible = state.phase == GemmaImeMediaPhase.RESULT
        errorText.isVisible = state.phase == GemmaImeMediaPhase.ERROR && !state.error.isNullOrBlank()
        errorText.text = state.error.orEmpty()
        mediaStatus.isVisible = !(
            state.modality == GemmaInputModality.IMAGE &&
                state.hasMedia &&
                state.phase == GemmaImeMediaPhase.READY
            )
        mediaStatus.text = state.status
        actionLabel.text = context.getString(
            if (state.modality == GemmaInputModality.IMAGE) {
                R.string.gemma_image_action_selector_title
            } else {
                R.string.gemma_audio_action_selector_title
            },
        )
        imageSelectionGroup.isVisible =
            state.modality == GemmaInputModality.IMAGE &&
                state.hasMedia &&
                state.phase != GemmaImeMediaPhase.RESULT
        if (!state.hasMedia) imagePreview.setImageDrawable(null)
        imageSourceLabel.text = when (state.imageSource) {
            GemmaImeImageSource.CLIPBOARD ->
                context.getString(R.string.gemma_image_source_clipboard)
            GemmaImeImageSource.DEVICE ->
                context.getString(R.string.gemma_image_source_device)
            null -> ""
        }

        buttonRecord.isEnabled = state.phase != GemmaImeMediaPhase.RECORDING
        buttonStopRecording.isEnabled = state.phase == GemmaImeMediaPhase.RECORDING
        buttonClipboardImage.isEnabled = state.phase != GemmaImeMediaPhase.RUNNING
        buttonDeviceImage.isEnabled = state.phase != GemmaImeMediaPhase.RUNNING
        buttonRun.isEnabled =
            state.hasMedia &&
                actions.isNotEmpty() &&
                state.phase in setOf(GemmaImeMediaPhase.READY, GemmaImeMediaPhase.ERROR)

        resultText.text = state.result
        resultCandidates.isVisible = state.candidates.size > 1
        if (state.candidates.isNotEmpty()) {
            resultCandidates.adapter = spinnerAdapter(state.candidates)
        }
    }

    private fun selectedResult(): String {
        return if (state.candidates.isNotEmpty()) {
            state.candidates.getOrNull(binding.resultCandidates.selectedItemPosition)
                ?: state.result
        } else {
            state.result
        }.trim()
    }

    private fun currentMediaReadyStatus(): String {
        val file = state.mediaPath?.let(::File)
        return when (state.modality) {
            GemmaInputModality.IMAGE -> when (state.imageSource) {
                GemmaImeImageSource.CLIPBOARD ->
                    context.getString(R.string.gemma_image_source_clipboard)
                GemmaImeImageSource.DEVICE ->
                    context.getString(R.string.gemma_image_source_device)
                null -> file?.name.orEmpty()
            }
            GemmaInputModality.AUDIO -> context.getString(
                R.string.gemma_audio_recorded,
                recordedDurationSeconds,
            )
            GemmaInputModality.TEXT -> ""
        }
    }

    private fun elapsedRecordingSeconds(): Long {
        if (recordingStartedAt <= 0L) return 0L
        return ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1_000L)
            .coerceIn(0L, MAX_RECORDING_MS / 1_000L)
    }

    private fun mediaCacheDirectory(): File {
        return File(context.cacheDir, MEDIA_CACHE_DIRECTORY).apply { mkdirs() }
    }

    private fun deleteOwnedMedia(path: String?) {
        val file = path?.let(::File) ?: return
        val directory = runCatching { mediaCacheDirectory().canonicalFile }.getOrNull() ?: return
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (candidate.parentFile == directory) candidate.delete()
    }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(
        context,
        R.layout.item_gemma_action_spinner,
        R.id.action_text,
        items,
    ).apply {
        setDropDownViewResource(R.layout.item_gemma_action_spinner_dropdown)
    }

    companion object {
        private const val MEDIA_CACHE_DIRECTORY = "gemma_media"
        private const val SAMPLE_RATE = 16_000
        private const val MIN_AUDIO_BUFFER = 4_096
        private const val MAX_RECORDING_MS = 30_000L
    }
}
