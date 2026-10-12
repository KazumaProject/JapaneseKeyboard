package com.kazumaproject.markdownhelperkeyboard.ime_service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Read local preedit after an outstanding editor-selection check, in gesture order. */
internal class SelectionOrderedInput(
    private val scope: CoroutineScope,
    private val hasPendingSelection: () -> Boolean,
    private val awaitSelection: suspend () -> Unit,
) {
    private val inputs = ArrayDeque<() -> Unit>()
    private var worker: Job? = null
    private var applying = false
    private var generation = 0L

    val isApplying: Boolean get() = applying

    fun defer(isCurrentSession: () -> Boolean, action: () -> Unit): Boolean {
        if (applying || (worker?.isActive != true && !hasPendingSelection())) return false
        inputs.addLast { if (isCurrentSession()) action() }
        if (worker?.isActive != true) {
            val token = generation
            worker = scope.launch {
                try {
                    while (inputs.isNotEmpty()) {
                        awaitSelection()
                        if (token != generation) return@launch
                        val next = inputs.removeFirst()
                        applying = true
                        try { next() } finally { applying = false }
                    }
                } finally {
                    if (token == generation) {
                        inputs.clear()
                        worker = null
                    }
                }
            }
        }
        return true
    }

    fun cancel() {
        generation++
        worker?.cancel()
        worker = null
        inputs.clear()
    }
}
