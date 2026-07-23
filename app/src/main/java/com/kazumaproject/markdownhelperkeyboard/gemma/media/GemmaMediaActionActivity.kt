package com.kazumaproject.markdownhelperkeyboard.gemma.media

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.webkit.MimeTypeMap
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ActivityGemmaMediaActionBinding
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaPromptBuilder
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.output
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.repository.GemmaPromptTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class GemmaMediaActionActivity : AppCompatActivity() {

    @Inject lateinit var actionRepository: GemmaPromptTemplateRepository
    @Inject lateinit var gemmaManager: GemmaTranslationManager
    @Inject lateinit var resultStore: GemmaMediaResultStore
    @Inject lateinit var appPreference: AppPreference

    private lateinit var binding: ActivityGemmaMediaActionBinding
    private var modality = GemmaInputModality.IMAGE
    private var mediaFile: File? = null
    private var actions: List<GemmaPromptTemplate> = emptyList()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile private var isRecording = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            if (mediaFile == null) finish()
        } else {
            importSelectedImage(uri)
        }
    }

    private val requestRecordAudio =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording() else showMessage(getString(R.string.gemma_audio_permission_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGemmaMediaActionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        modality = when (intent.getStringExtra(EXTRA_MODALITY)) {
            GemmaInputModality.AUDIO.name -> GemmaInputModality.AUDIO
            else -> GemmaInputModality.IMAGE
        }
        title = if (modality == GemmaInputModality.IMAGE) {
            getString(R.string.gemma_image_action_title)
        } else {
            getString(R.string.gemma_audio_action_title)
        }
        setupViews()
        loadActions()

        if (modality == GemmaInputModality.IMAGE) {
            val suppliedPath = intent.getStringExtra(EXTRA_MEDIA_PATH)
            val supplied = suppliedPath?.let(::File)?.takeIf(File::isFile)
            if (supplied != null) {
                setImageFile(supplied)
            } else {
                pickImage.launch(arrayOf("image/*"))
            }
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom,
            )
            insets
        }
    }

    private fun setupViews() = with(binding) {
        imagePreview.isVisible = modality == GemmaInputModality.IMAGE
        buttonChooseImage.isVisible = modality == GemmaInputModality.IMAGE
        audioControls.isVisible = modality == GemmaInputModality.AUDIO
        buttonChooseImage.setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        buttonRecord.setOnClickListener { ensureAudioPermissionAndRecord() }
        buttonStopRecording.setOnClickListener { stopRecording() }
        buttonRun.setOnClickListener { runSelectedAction() }
        buttonSendToKeyboard.setOnClickListener {
            val result = editResult.text?.toString()?.trim().orEmpty()
            if (result.isNotEmpty()) {
                resultStore.put(result)
                finish()
            }
        }
        buttonCopyResult.setOnClickListener {
            val result = editResult.text?.toString().orEmpty()
            if (result.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Gemma", result))
                showMessage(getString(R.string.gemma_result_copied))
            }
        }
        buttonCancel.setOnClickListener { finish() }
        resultCandidates.onItemSelectedListener = SimpleItemSelectedListener { position ->
            val candidate = resultCandidates.getItemAtPosition(position)?.toString().orEmpty()
            if (candidate.isNotEmpty()) editResult.setText(candidate)
        }
        updateRunButton()
    }

    private fun loadActions() {
        lifecycleScope.launch {
            actions = withContext(Dispatchers.IO) { actionRepository.getEnabledActions(modality) }
            binding.spinnerAction.adapter = spinnerAdapter(actions.map { it.title })
            binding.emptyActions.isVisible = actions.isEmpty()
            updateRunButton()
        }
    }

    private fun importSelectedImage(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val directory = mediaCacheDirectory()
                    val extension = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(contentResolver.getType(uri))
                        ?.lowercase()
                        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                        ?: "png"
                    val target = File(directory, "image_${System.currentTimeMillis()}.$extension")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the selected image." }
                        target.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    target
                }
            }.onSuccess(::setImageFile)
                .onFailure { showMessage(it.localizedMessage ?: getString(R.string.gemma_media_import_failed)) }
        }
    }

    private fun setImageFile(file: File) {
        mediaFile?.takeIf { it != file && it.parentFile == mediaCacheDirectory() }?.delete()
        mediaFile = file
        binding.imagePreview.setImageURI(Uri.fromFile(file))
        binding.mediaStatus.text = file.name
        updateRunButton()
    }

    private fun ensureAudioPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showMessage(getString(R.string.gemma_audio_permission_denied))
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            showMessage(getString(R.string.gemma_audio_unavailable))
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
            showMessage(it.localizedMessage ?: getString(R.string.gemma_audio_unavailable))
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            showMessage(getString(R.string.gemma_audio_unavailable))
            return
        }

        val rawFile = File(mediaCacheDirectory(), "audio_${System.currentTimeMillis()}.pcm")
        val wavFile = File(mediaCacheDirectory(), "audio_${System.currentTimeMillis()}.wav")
        audioRecord = recorder
        isRecording = true
        binding.buttonRecord.isEnabled = false
        binding.buttonStopRecording.isEnabled = true
        binding.mediaStatus.text = getString(R.string.gemma_audio_recording)
        recorder.startRecording()
        val startedAt = SystemClock.elapsedRealtime()
        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBuffer.coerceAtLeast(MIN_AUDIO_BUFFER))
            FileOutputStream(rawFile).use { output ->
                while (isActive && isRecording &&
                    SystemClock.elapsedRealtime() - startedAt < MAX_RECORDING_MS
                ) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) output.write(buffer, 0, read)
                }
            }
            runCatching { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            recorder.release()
            audioRecord = null
            isRecording = false
            writeWav(rawFile, wavFile)
            rawFile.delete()
            withContext(Dispatchers.Main) {
                mediaFile?.takeIf { it.parentFile == mediaCacheDirectory() }?.delete()
                mediaFile = wavFile
                binding.buttonRecord.isEnabled = true
                binding.buttonStopRecording.isEnabled = false
                binding.mediaStatus.text = getString(
                    R.string.gemma_audio_recorded,
                    ((SystemClock.elapsedRealtime() - startedAt) / 1000L).coerceAtMost(30L),
                )
                updateRunButton()
            }
        }
        lifecycleScope.launch {
            while (isRecording) {
                delay(250)
                if (!isRecording) break
                val seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).coerceAtMost(30L)
                binding.mediaStatus.text = getString(R.string.gemma_audio_recording_seconds, seconds)
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        runCatching { audioRecord?.stop() }
    }

    private fun runSelectedAction() {
        val file = mediaFile ?: return
        val action = actions.getOrNull(binding.spinnerAction.selectedItemPosition) ?: return
        binding.progress.isVisible = true
        binding.buttonRun.isEnabled = false
        binding.resultGroup.isVisible = false
        lifecycleScope.launch {
            runCatching {
                val prompt = GemmaPromptBuilder.build(
                    action,
                    appPreference.gemma_translation_target_language_preference,
                )
                gemmaManager.runMediaPrompt(
                    prompt = prompt,
                    mediaPath = file.absolutePath,
                    mediaType = if (modality == GemmaInputModality.IMAGE) {
                        GemmaMediaType.IMAGE
                    } else {
                        GemmaMediaType.AUDIO
                    },
                )
            }.onSuccess { raw ->
                if (action.output() == GemmaOutputMode.CANDIDATE_LIST) {
                    val candidates = GemmaPromptBuilder.parseCandidates(raw)
                    binding.resultCandidates.adapter = spinnerAdapter(candidates)
                    binding.resultCandidates.isVisible = candidates.size > 1
                    binding.editResult.setText(candidates.firstOrNull().orEmpty())
                } else {
                    binding.resultCandidates.isVisible = false
                    binding.editResult.setText(raw)
                }
                binding.resultGroup.isVisible = true
            }.onFailure {
                showMessage(it.localizedMessage ?: getString(R.string.gemma_media_action_failed))
            }
            binding.progress.isVisible = false
            updateRunButton()
        }
    }

    private fun updateRunButton() {
        binding.buttonRun.isEnabled = mediaFile?.isFile == true && actions.isNotEmpty() && !isRecording
    }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_item,
        items,
    ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun mediaCacheDirectory(): File = File(cacheDir, "gemma_media").apply { mkdirs() }

    private fun writeWav(rawFile: File, wavFile: File) {
        val rawSize = rawFile.length()
        FileOutputStream(wavFile).use { output ->
            output.write(wavHeader(rawSize))
            FileInputStream(rawFile).use { input -> input.copyTo(output) }
        }
    }

    private fun wavHeader(dataSize: Long): ByteArray {
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + dataSize).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)
            putShort(BYTES_PER_SAMPLE.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize.toInt())
        }.array()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private class SimpleItemSelectedListener(
        private val action: (Int) -> Unit,
    ) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            action(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    companion object {
        const val EXTRA_MODALITY = "gemma_media_modality"
        const val EXTRA_MEDIA_PATH = "gemma_media_path"
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val MIN_AUDIO_BUFFER = 4_096
        private const val MAX_RECORDING_MS = 30_000L
    }
}
