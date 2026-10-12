package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.Spanned
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Selection callbacks describe an earlier editor state; verify before clearing local preedit. */
internal class EditorSelectionCleanup(
    private val scope: CoroutineScope,
    private val connection: () -> InputConnection?,
    private val revision: () -> Long,
    private val readMutex: Mutex,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val composingRange: () -> IntRange? = { null },
) {
    private data class Snapshot(
        val extracted: ExtractedText?,
        val surrounding: List<CharSequence?>,
    )
    private var request = 0L
    private var job: Job? = null

    val hasPending: Boolean get() = job?.isActive == true

    suspend fun awaitPending() {
        while (true) {
            val pending = job?.takeIf { it.isActive } ?: return
            pending.join()
        }
    }

    fun cancel() {
        request++
        job?.cancel()
        job = null
    }

    fun verify(start: Int, end: Int, onCurrentSelection: () -> Unit) {
        verifySnapshot(start, end, true) { _, _ -> onCurrentSelection() }
    }

    fun verifyBeforeInput(start: Int, end: Int, onCurrentSelection: (Int, Int) -> Unit) {
        verifySnapshot(start, end, false, onCurrentSelection)
    }

    private fun verifySnapshot(start: Int, end: Int, matchSelection: Boolean, onCurrentSelection: (Int, Int) -> Unit) {
        cancel()
        val editor = connection() ?: return
        val token = request
        val version = revision()
        job = scope.launch {
            val snapshot = readMutex.withLock {
                withContext(readDispatcher) {
                    val extracted = runCatching {
                        editor.getExtractedText(ExtractedTextRequest().apply {
                            flags = InputConnection.GET_TEXT_WITH_STYLES
                        }, 0)
                    }.getOrNull()
                    // Some editors refuse extraction but still support styled cursor reads.
                    // Preserve their normal external-caret cleanup rather than ignoring it.
                    val surrounding = if (extracted?.text == null || extracted.partialStartOffset >= 0) {
                        listOf(
                            runCatching { editor.getTextBeforeCursor(1, InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                            runCatching { editor.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                            runCatching { editor.getTextAfterCursor(1, InputConnection.GET_TEXT_WITH_STYLES) }.getOrNull(),
                        )
                    } else emptyList()
                    Snapshot(extracted, surrounding)
                }
            }
            if (token != request || version != revision() || connection() !== editor) return@launch
            val extracted = snapshot.extracted
            if (extracted?.text == null || extracted.partialStartOffset >= 0) {
                // A completely unreadable editor is not evidence that new input ended.
                if (snapshot.surrounding.all { it == null } || snapshot.surrounding.any(::isComposing)) return@launch
                // Plain text does not prove that an existing composing region ended.
                // With no absolute caret snapshot, retain the native composing range.
                if (!matchSelection && composingRange() != null && snapshot.surrounding.none { it is Spanned }) return@launch
                onCurrentSelection(start, end)
                return@launch
            }
            val text = extracted.text
            if (extracted.startOffset < 0 || extracted.partialStartOffset >= 0 ||
                extracted.selectionStart !in 0..text.length || extracted.selectionEnd !in 0..text.length ||
                (matchSelection && (extracted.startOffset + extracted.selectionStart != start ||
                extracted.startOffset + extracted.selectionEnd != end))
            ) return@launch
            if (isComposing(text)) return@launch
            val activeRange = composingRange()
            if (!matchSelection && activeRange != null &&
                extracted.startOffset + extracted.selectionStart in activeRange &&
                extracted.startOffset + extracted.selectionEnd in activeRange
            ) return@launch
            onCurrentSelection(extracted.startOffset + extracted.selectionStart, extracted.startOffset + extracted.selectionEnd)
        }
    }

    private fun isComposing(text: CharSequence?) = text is Spanned &&
        text.getSpans(0, text.length, Any::class.java).any {
            text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0
        }
}
