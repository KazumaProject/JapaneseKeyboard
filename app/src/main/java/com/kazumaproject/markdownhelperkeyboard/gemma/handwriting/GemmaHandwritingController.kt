package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaImageCapability
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import java.io.File
import androidx.core.view.isVisible
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class GemmaHandwritingController(
    private val context: Context,
    private val gemmaManager: GemmaTranslationManager,
    private val callbacks: Callbacks,
    private val settingsProvider: () -> GemmaHandwritingSettings = {
        GemmaHandwritingSettings()
    },
) {
    interface Callbacks {
        fun onVisibilityChanged(visible: Boolean)
        fun currentInputSessionId(): Long
        fun commitRecognizedText(text: String, inputSessionId: Long): Boolean
        fun deleteText()
        fun moveCursor(keyCode: Int)
        fun showMessage(message: String)
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val strokeStore = HandwritingStrokeStore()
    private var view: GemmaHandwritingKeyboardView? = null
    private var autoRecognitionJob: Job? = null
    private var inferenceJob: Job? = null
    private var temporaryImage: File? = null
    private var candidates: List<String> = emptyList()
    private var errorMessage: String? = null
    private var inputSessionIdAtOpen: Long = -1L
    private var strokeInProgress = false

    var isActive: Boolean = false
        private set

    fun bindView(view: GemmaHandwritingKeyboardView) {
        this.view?.isVisible = false
        this.view = view
        view.bindStore(strokeStore)
        view.onStrokeStarted = ::onDrawingStarted
        view.onStrokeCommitted = ::onDrawingChanged
        view.onStrokeCancelled = ::onDrawingCancelled
        view.onUndo = {
            if (strokeStore.undo()) onDrawingChanged()
        }
        view.onRedo = {
            if (strokeStore.redo()) onDrawingChanged()
        }
        view.onClear = {
            if (strokeStore.clear()) onDrawingChanged()
        }
        view.onRecognize = ::recognize
        view.onReturnToKeyboard = { close() }
        view.onDeleteText = callbacks::deleteText
        view.onCursorKey = callbacks::moveCursor
        view.onCandidateSelected = ::commitCandidate
        render()
    }

    fun open(): Boolean {
        if (gemmaManager.imageInputCapability() !is GemmaImageCapability.Available) {
            callbacks.showMessage(context.getString(R.string.gemma_handwriting_unavailable))
            return false
        }
        cancelInference()
        strokeStore.reset()
        candidates = emptyList()
        errorMessage = null
        strokeInProgress = false
        inputSessionIdAtOpen = callbacks.currentInputSessionId()
        isActive = true
        callbacks.onVisibilityChanged(true)
        render()
        return true
    }

    fun close() {
        if (!isActive) return
        cancelInference()
        deleteTemporaryImage()
        strokeStore.reset()
        candidates = emptyList()
        errorMessage = null
        strokeInProgress = false
        isActive = false
        callbacks.onVisibilityChanged(false)
        render()
    }

    fun onInputSessionChanged() {
        if (isActive) close()
    }

    fun onInputViewHidden() {
        if (isActive) close()
    }

    fun onImageCapabilityChanged(capability: GemmaImageCapability) {
        if (isActive && capability !is GemmaImageCapability.Available) {
            close()
            callbacks.showMessage(context.getString(R.string.gemma_handwriting_unavailable))
        }
    }

    fun destroy() {
        cancelInference()
        deleteTemporaryImage()
        strokeStore.reset()
        strokeInProgress = false
        view = null
        controllerScope.cancel()
    }

    private fun onDrawingStarted() {
        if (!isActive) return
        strokeInProgress = true
        val needsRender =
            inferenceJob != null || candidates.isNotEmpty() || !errorMessage.isNullOrBlank()
        cancelInference()
        candidates = emptyList()
        errorMessage = null
        if (needsRender) render()
    }

    private fun onDrawingChanged() {
        if (!isActive) return
        strokeInProgress = false
        cancelInference()
        candidates = emptyList()
        errorMessage = null
        render()
        scheduleAutoRecognition()
    }

    private fun onDrawingCancelled() {
        if (!isActive) return
        strokeInProgress = false
        render()
        scheduleAutoRecognition()
    }

    private fun recognize() {
        if (!isActive || strokeInProgress || inferenceJob != null) return
        cancelPendingAutoRecognition()
        if (strokeStore.isEmpty) {
            callbacks.showMessage(context.getString(R.string.gemma_handwriting_no_strokes))
            return
        }
        if (gemmaManager.imageInputCapability() !is GemmaImageCapability.Available) {
            callbacks.showMessage(context.getString(R.string.gemma_handwriting_unavailable))
            return
        }

        val revision = strokeStore.revision
        val strokes = strokeStore.strokes
        val sessionId = inputSessionIdAtOpen
        val settings = settingsProvider()
        candidates = emptyList()
        errorMessage = null

        val imageFile = File(
            context.cacheDir,
            "gemma_handwriting/handwriting_${sessionId}_$revision.png",
        )
        temporaryImage = imageFile
        val recognitionJob = controllerScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(Dispatchers.IO) {
                    HandwritingBitmapExporter.writePng(
                        strokes = strokes,
                        target = imageFile,
                        penSizeDp = settings.resolvedRecognitionPenSizeDp(),
                        strokeColor = settings.resolvedRecognitionPenColor(),
                    )
                }
                val raw = withTimeout(RECOGNITION_TIMEOUT_MS) {
                    gemmaManager.runMediaPrompt(
                        prompt = settings.recognitionPrompt,
                        mediaPath = imageFile.absolutePath,
                        mediaType = GemmaMediaType.IMAGE,
                    )
                }
                candidates = GemmaHandwritingPrompt.parseCandidates(
                    raw = raw,
                    language = settings.recognitionLanguage,
                )
                if (
                    !isActive ||
                    strokeStore.revision != revision ||
                    callbacks.currentInputSessionId() != sessionId
                ) {
                    return@launch
                }
                errorMessage = if (candidates.isEmpty()) {
                    context.getString(R.string.gemma_handwriting_no_result)
                } else {
                    null
                }
                Timber.i(
                    "Gemma handwriting recognition completed: candidateCount=%d candidates=%s",
                    candidates.size,
                    candidates,
                )
            } catch (error: TimeoutCancellationException) {
                gemmaManager.cancelActiveTranslation()
                if (isActive && strokeStore.revision == revision) {
                    errorMessage = context.getString(R.string.gemma_handwriting_timed_out)
                    Timber.w("Gemma handwriting recognition timed out")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isActive && strokeStore.revision == revision) {
                    errorMessage = error.localizedMessage
                        ?: context.getString(R.string.gemma_handwriting_no_result)
                    Timber.e(error, "Gemma handwriting recognition failed")
                }
            } finally {
                runCatching { imageFile.delete() }
                if (temporaryImage == imageFile) temporaryImage = null
                if (inferenceJob === coroutineContext[Job]) {
                    inferenceJob = null
                    render()
                }
            }
        }
        inferenceJob = recognitionJob
        render(recognizing = true)
        recognitionJob.start()
    }

    private fun commitCandidate(candidate: String) {
        if (!isActive || candidate.isBlank()) return
        if (!callbacks.commitRecognizedText(candidate, inputSessionIdAtOpen)) {
            errorMessage = context.getString(R.string.gemma_handwriting_session_changed)
            render()
            return
        }
        Timber.i("Gemma handwriting candidate committed: %s", candidate)
        strokeStore.reset()
        candidates = emptyList()
        errorMessage = null
        render()
    }

    private fun cancelInference() {
        cancelPendingAutoRecognition()
        val jobToCancel = inferenceJob
        inferenceJob = null
        if (jobToCancel != null) {
            gemmaManager.cancelActiveTranslation()
        }
        jobToCancel?.cancel()
        deleteTemporaryImage()
    }

    private fun scheduleAutoRecognition() {
        if (!isActive || strokeInProgress || strokeStore.isEmpty) return
        autoRecognitionJob = controllerScope.launch {
            delay(settingsProvider().autoRecognitionDelayMs)
            autoRecognitionJob = null
            recognize()
        }
    }

    private fun cancelPendingAutoRecognition() {
        autoRecognitionJob?.cancel()
        autoRecognitionJob = null
    }

    private fun deleteTemporaryImage() {
        temporaryImage?.let { file ->
            runCatching { file.delete() }
        }
        temporaryImage = null
    }

    private fun render(recognizing: Boolean = inferenceJob != null) {
        val target = view ?: return
        target.isVisible = isActive
        target.applySettings(settingsProvider())
        target.updateHistoryButtons(
            canUndo = strokeStore.canUndo,
            canRedo = strokeStore.canRedo,
        )
        when {
            recognizing -> target.showRecognizing()
            candidates.isNotEmpty() -> target.showCandidates(candidates)
            !errorMessage.isNullOrBlank() ->
                target.showError(errorMessage.orEmpty(), hasStrokes = !strokeStore.isEmpty)
            else -> target.showReady(hasStrokes = !strokeStore.isEmpty)
        }
        target.refreshCanvas()
    }

    private companion object {
        const val RECOGNITION_TIMEOUT_MS = 30_000L
    }
}
