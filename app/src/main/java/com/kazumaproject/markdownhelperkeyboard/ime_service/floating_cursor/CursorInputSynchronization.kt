package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.text.Spanned
import android.util.Log
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The requested text of the last accepted composing write, before its range notification catches up. */
internal data class CursorComposingExpectation(val start: Int?, val text: String)

/** Selection notifications are snapshots. Confirm a snapshot before discarding local preedit. */
internal class CursorInputSynchronization(
    private val scope: CoroutineScope,
    private val connection: () -> InputConnection?,
    private val revision: () -> Long,
    private val mutex: Mutex,
    private val composingRange: () -> IntRange?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val expectedComposing: () -> CursorComposingExpectation? = { null },
    private val compositionProbe: suspend (InputConnection, Int, Int) -> Boolean? = { _, _, _ -> null },
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
        trace("verify token=$token version=$version notification=$start,$end match=$matchNotification")
        checkJob = scope.launch {
            val snapshot = mutex.withLock {
                withContext(dispatcher) {
                    val extracted = runCatching { editor.getExtractedText(
                        ExtractedTextRequest().apply { flags = InputConnection.GET_TEXT_WITH_STYLES }, 0) }.getOrNull()
                    val text = extracted?.text
                    if (text != null && extracted.startOffset >= 0 && extracted.partialStartOffset < 0 &&
                        extracted.selectionStart in 0..text.length && extracted.selectionEnd in 0..text.length
                    ) Snapshot(extracted.startOffset + extracted.selectionStart, extracted.startOffset + extracted.selectionEnd,
                        listOf(text), extracted.startOffset)
                    else Snapshot(start, end, listOf(
                        runCatching { editor.getTextBeforeCursor(1, InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                        runCatching { editor.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                        runCatching { editor.getTextAfterCursor(1, InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull()), null)
                }
            }
            if (token != generation || version != revision() || connection() !== editor) return@launch
            if (snapshot.texts.all { it == null } || snapshot.texts.any(::hasComposingSpan)) return@launch
            if (matchNotification && snapshot.absolute && (snapshot.start != start || snapshot.end != end)) return@launch
            // Plain strings cannot establish that composing spans are absent. In particular,
            // the editor's latest text can arrive before its latest composing-range callback.
            // A styled snapshot without composing spans is evidence of native completion,
            // including when the caret is still inside a previously composing range.
            val stylesAvailable = if (snapshot.absolute) snapshot.texts.singleOrNull() is Spanned else
                snapshot.texts[0] is Spanned && snapshot.texts[2] is Spanned &&
                    (snapshot.texts[1] == null || snapshot.texts[1] is Spanned)
            trace("snapshot token=$token selection=${snapshot.start},${snapshot.end} offset=${snapshot.offset} styles=$stylesAvailable lengths=${snapshot.texts.map { it?.length }}")
            if (!stylesAvailable) {
                val nativeComposing = if (expectedComposing() != null || composingRange() != null)
                    compositionProbe(editor, snapshot.start, snapshot.end) else null
                trace("probe token=$token nativeComposing=$nativeComposing current=${token == generation && version == revision() && connection() === editor}")
                // The probe suspends while an editor callback arrives. A composing write,
                // newer notification or input-target change may invalidate the earlier read.
                if (token != generation || version != revision() || connection() !== editor) return@launch
                if (nativeComposing == true ||
                    (nativeComposing == null && preservesUnstyledComposition(snapshot))) return@launch
            }
            trace("apply token=$token selection=${snapshot.start},${snapshot.end}")
            currentSelection(snapshot.start, snapshot.end)
        }
    }

    private fun preservesUnstyledComposition(snapshot: Snapshot): Boolean {
        // The surrounding-text fallback provides neither current absolute endpoints nor the
        // whole composing text. A stale notification and stripped styles are not sufficient.
        val offset = snapshot.offset ?: return true
        val range = composingRange()
        if (range != null && snapshot.start in range && snapshot.end in range) return true
        val expected = expectedComposing()?.takeIf { it.text.isNotEmpty() }
        if (expected != null) {
            val expectedStart = expected.start?.takeIf { it >= 0 } ?: return true
            val expectedEnd = expectedStart.toLong() + expected.text.length
            val nativeText = snapshot.texts.singleOrNull() ?: return true
            val localStart = expectedStart.toLong() - offset
            val localEnd = localStart + expected.text.length
            // A windowed extraction can omit part of the expected composing region.
            if (localStart < 0 || localEnd > nativeText.length) return true
            // InputFilters can change an accepted write's text without ending native
            // composition. A text mismatch cannot distinguish that from an external edit.
            if (nativeText.subSequence(localStart.toInt(), localEnd.toInt()).toString() != expected.text) return true
            return snapshot.start.toLong() in expectedStart.toLong()..expectedEnd &&
                snapshot.end.toLong() in expectedStart.toLong()..expectedEnd
        }
        return false
    }

    private data class Snapshot(val start: Int, val end: Int, val texts: List<CharSequence?>, val offset: Int?) {
        val absolute get() = offset != null
    }
    private fun hasComposingSpan(text: CharSequence?): Boolean = text is Spanned &&
        text.getSpans(0, text.length, Any::class.java).any { text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 }

    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d("CursorInputSync", message)
    }
}
