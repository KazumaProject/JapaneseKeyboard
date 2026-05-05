package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideInputListener
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

class QwertyGlideInputCoordinator(
    private val scope: CoroutineScope,
    private val englishEngine: EnglishEngine,
    private val previousTextProvider: () -> String,
    private val onPreviewCandidates: (List<Candidate>) -> Unit,
    private val onFinalCandidates: (List<Candidate>) -> Unit,
    private val onCancel: () -> Unit = {}
) : QwertyGlideInputListener {
    private val generation = AtomicLong(0L)
    private var previewJob: Job? = null
    private var finalJob: Job? = null

    override fun onQwertyGlideStarted() {
        generation.incrementAndGet()
        previewJob?.cancel()
        finalJob?.cancel()
    }

    override fun onQwertyGlideUpdated(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    ) {
        val requestGeneration = generation.get()
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val candidates = withContext(Dispatchers.Default) {
                englishEngine.getGlideCandidates(
                    inputPointers = inputPointers,
                    proximityInfo = proximityInfo,
                    previousText = previousTextProvider(),
                    limit = 6
                )
            }
            if (generation.get() == requestGeneration) {
                if (BuildConfig.DEBUG) {
                    Timber.d("QWERTY glide preview: points=${inputPointers.points.size} top=${candidates.take(5).map { it.string }}")
                }
                onPreviewCandidates(candidates)
            }
        }
    }

    override fun onQwertyGlideEnded(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    ) {
        val requestGeneration = generation.incrementAndGet()
        previewJob?.cancel()
        finalJob?.cancel()
        finalJob = scope.launch {
            val candidates = withContext(Dispatchers.Default) {
                englishEngine.getGlideCandidates(
                    inputPointers = inputPointers,
                    proximityInfo = proximityInfo,
                    previousText = previousTextProvider(),
                    limit = 12
                )
            }
            if (generation.get() == requestGeneration) {
                if (BuildConfig.DEBUG) {
                    Timber.d("QWERTY glide final: points=${inputPointers.points.size} top=${candidates.take(8).map { it.string to it.score }}")
                }
                onFinalCandidates(candidates)
            }
        }
    }

    override fun onQwertyGlideCancelled() {
        generation.incrementAndGet()
        previewJob?.cancel()
        finalJob?.cancel()
        onCancel()
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 96L
    }
}
