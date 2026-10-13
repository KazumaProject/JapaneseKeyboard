package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.text.Spanned
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Selection notifications are snapshots. Confirm a snapshot before discarding local preedit. */
internal class CursorInputSynchronization(
    private val scope: CoroutineScope,
    private val connection: () -> InputConnection?,
    private val revision: () -> Long,
    private val mutex: Mutex,
    private val composingRange: () -> IntRange?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var generation = 0L
    private var checkJob: Job? = null
    private var inputJob: Job? = null
    private val inputs = ArrayDeque<() -> Unit>()
    private var inputGeneration = 0L
    var applyingInput = false
        private set
    val checking get() = checkJob?.isActive == true

    fun invalidateSnapshot() {
        generation++
        checkJob?.cancel()
        checkJob = null
    }

    fun reset() {
        invalidateSnapshot()
        inputGeneration++
        inputJob?.cancel(); inputJob = null
        inputs.clear()
    }

    suspend fun awaitSnapshot() {
        while (true) {
            val pending = checkJob?.takeIf { it.isActive } ?: return
            pending.join()
        }
    }

    /** Returns true when the caller must let the queued action perform its input later. */
    fun defer(current: () -> Boolean, action: () -> Unit): Boolean {
        if (applyingInput || (!checking && inputJob?.isActive != true)) return false
        inputs.addLast { if (current()) action() }
        val token = inputGeneration
        if (inputJob?.isActive != true) inputJob = scope.launch {
            try {
                while (inputs.isNotEmpty()) {
                    awaitSnapshot()
                    if (token != inputGeneration) return@launch
                    val next = inputs.removeFirst()
                    applyingInput = true
                    try { next() } finally { applyingInput = false }
                }
            } finally { if (token == inputGeneration) { inputs.clear(); inputJob = null } }
        }
        return true
    }

    fun verifyNotification(start: Int, end: Int, currentSelection: (Int, Int) -> Unit) =
        verify(start, end, matchNotification = true, currentSelection)

    fun verifyBeforeInput(start: Int, end: Int, currentSelection: (Int, Int) -> Unit) =
        verify(start, end, matchNotification = false, currentSelection)

    private fun verify(start: Int, end: Int, matchNotification: Boolean, currentSelection: (Int, Int) -> Unit) {
        invalidateSnapshot()
        val editor = connection() ?: return
        val token = generation
        val version = revision()
        checkJob = scope.launch {
            val snapshot = mutex.withLock {
                withContext(dispatcher) {
                    val extracted = runCatching { editor.getExtractedText(
                        ExtractedTextRequest().apply { flags = InputConnection.GET_TEXT_WITH_STYLES }, 0) }.getOrNull()
                    val text = extracted?.text
                    if (text != null && extracted.startOffset >= 0 && extracted.partialStartOffset < 0 &&
                        extracted.selectionStart in 0..text.length && extracted.selectionEnd in 0..text.length
                    ) Snapshot(extracted.startOffset + extracted.selectionStart, extracted.startOffset + extracted.selectionEnd,
                        listOf(text), true)
                    else Snapshot(start, end, listOf(
                        runCatching { editor.getTextBeforeCursor(1, InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                        runCatching { editor.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                        runCatching { editor.getTextAfterCursor(1, InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull()), false)
                }
            }
            if (token != generation || version != revision() || connection() !== editor) return@launch
            if (snapshot.texts.all { it == null } || snapshot.texts.any(::hasComposingSpan)) return@launch
            if (matchNotification && snapshot.absolute && (snapshot.start != start || snapshot.end != end)) return@launch
            if (!matchNotification) {
                val range = composingRange()
                // An editor can strip styles from extraction. A caret still inside the known
                // composing range does not establish that native composition has ended.
                if (range != null && ((snapshot.absolute && snapshot.start in range && snapshot.end in range) ||
                        (!snapshot.absolute && snapshot.texts.none { it is Spanned }))) return@launch
            }
            currentSelection(snapshot.start, snapshot.end)
        }
    }

    private data class Snapshot(val start: Int, val end: Int, val texts: List<CharSequence?>, val absolute: Boolean)
    private fun hasComposingSpan(text: CharSequence?): Boolean = text is Spanned &&
        text.getSpans(0, text.length, Any::class.java).any { text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 }
}
