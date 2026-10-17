package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class GemmaFloatingInput(
    val session: Long,
    val revision: Long,
    val text: String,
    val rule: GemmaFloatingRule,
    val editorRevision: Long = 0,
)

/** Main-thread state, a single native inference, and one conflated pending update. */
internal class GemmaFloatingCoordinator(
    private val scope: CoroutineScope,
    private val generate: suspend (GemmaFloatingInput) -> String?,
    private val cancelGeneration: () -> Unit,
    private val publish: (GemmaFloatingInput?, String?) -> Unit,
    private val debounceMillis: Long = 300,
    private val publishProcessing: (GemmaFloatingInput) -> Unit = {},
) {
    private val updates = Channel<GemmaFloatingInput>(Channel.CONFLATED)
    private var debounceJob: Job? = null
    private var current: GemmaFloatingInput? = null
    private var active = false
    private var suspended = false
    private val worker = scope.launch {
        for (request in updates) {
            if (current != request || suspended) continue
            try {
                active = true
                publishProcessing(request)
                val result = generate(request)
                if (current == request && !suspended) {
                    if (result == null) schedule(request) // A foreground action owns the engine.
                    else publish(request, result.takeIf { it.isNotBlank() })
                }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                if (current == request) publish(request, null)
            } catch (_: Exception) {
                if (current == request) publish(request, null)
            } finally {
                active = false
            }
        }
    }

    private fun schedule(input: GemmaFloatingInput) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMillis)
            updates.trySend(input)
        }
    }

    fun update(input: GemmaFloatingInput?) {
        if (current == input) return
        current = input
        debounceJob?.cancel()
        suspended = false
        publish(null, null)
        if (active) cancelGeneration()
        if (input != null) schedule(input)
    }

    /** Explicit model actions keep priority until the composing text next changes. */
    fun suspendForForeground() {
        suspended = true
        debounceJob?.cancel()
        publish(null, null)
        if (active) cancelGeneration()
    }

    fun destroy() {
        update(null)
        debounceJob?.cancel()
        worker.cancel()
        updates.close()
    }
}
