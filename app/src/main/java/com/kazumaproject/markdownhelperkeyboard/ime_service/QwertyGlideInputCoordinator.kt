package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideCandidateProvider
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideInputListener
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

class QwertyGlideInputCoordinator(
    private val scope: CoroutineScope,
    private val candidateProvider: QwertyGlideCandidateProvider,
    private val previousTextProvider: () -> String,
    private val onPreviewCandidates: (List<Candidate>) -> Unit,
    private val onFinalCandidates: (List<Candidate>) -> Unit,
    private val onGlideStarted: () -> Unit = {},
    private val onCancel: () -> Unit = {},
    private val onProcessingChanged: (Boolean) -> Unit = {},
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : QwertyGlideInputListener {
    private val generation = AtomicLong(0L)
    private val processingLock = Any()
    private val processingTokenGeneration = AtomicLong(0L)
    private val activeProcessingTokens = mutableSetOf<Long>()
    private var previewJob: Job? = null
    private var finalJob: Job? = null

    override fun onQwertyGlideStarted() {
        generation.incrementAndGet()
        previewJob?.cancel()
        finalJob?.cancel()
        clearProcessing()
        onGlideStarted()
    }

    override fun onQwertyGlideUpdated(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    ) {
        val requestGeneration = generation.get()
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val processingToken = beginProcessing()
            try {
                val candidates = withContext(decodeDispatcher) {
                    candidateProvider.getGlideCandidates(
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Timber.e(error, "QWERTY glide preview decode failed.")
            } finally {
                finishProcessing(processingToken)
            }
        }
    }

    override fun onQwertyGlideEnded(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    ) {
        val requestGeneration = generation.incrementAndGet()
        val processingToken = beginProcessing()
        previewJob?.cancel()
        finalJob?.cancel()
        finalJob = scope.launch {
            try {
                val candidates = withContext(decodeDispatcher) {
                    candidateProvider.getGlideCandidates(
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Timber.e(error, "QWERTY glide final decode failed.")
            } finally {
                finishProcessing(processingToken)
            }
        }
    }

    override fun onQwertyGlideCancelled() {
        generation.incrementAndGet()
        previewJob?.cancel()
        finalJob?.cancel()
        clearProcessing()
        onCancel()
    }

    fun cancelPending() {
        generation.incrementAndGet()
        previewJob?.cancel()
        finalJob?.cancel()
        clearProcessing()
    }

    private fun beginProcessing(): Long {
        val token = processingTokenGeneration.incrementAndGet()
        val shouldShow = synchronized(processingLock) {
            val wasEmpty = activeProcessingTokens.isEmpty()
            activeProcessingTokens += token
            wasEmpty
        }
        if (shouldShow) onProcessingChanged(true)
        return token
    }

    private fun finishProcessing(token: Long) {
        val shouldHide = synchronized(processingLock) {
            val removed = activeProcessingTokens.remove(token)
            removed && activeProcessingTokens.isEmpty()
        }
        if (shouldHide) onProcessingChanged(false)
    }

    private fun clearProcessing() {
        val shouldHide = synchronized(processingLock) {
            val wasNotEmpty = activeProcessingTokens.isNotEmpty()
            activeProcessingTokens.clear()
            wasNotEmpty
        }
        if (shouldHide) onProcessingChanged(false)
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 96L
    }
}
