package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Main-thread confined queue. Each key's editor acknowledgement precedes the next key. */
internal class ForwardDeleteCoordinator(
    private val scope: CoroutineScope,
    private val currentConnection: () -> InputConnection?,
    private val currentRevision: () -> Long,
    private val canDelete: () -> Boolean,
    private val delete: (selectionWasActive: Boolean) -> Unit,
    private val recordDeletion: (String) -> Unit,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private data class Selection(val start: Int, val end: Int) {
        val valid get() = start >= 0 && end >= 0
        val selected get() = start != end
        fun collapsed() = Selection(minOf(start, end), minOf(start, end))
    }

    private data class Snapshot(val text: String, val offset: Int, val selection: Selection)
    private class Batch(val connection: InputConnection, var revision: Long, var pending: Int = 1)

    private var selection = Selection(-1, -1)
    private var batch: Batch? = null
    val hasSelection get() = selection.valid && selection.selected

    fun reset(start: Int, end: Int) {
        cancel()
        selection = Selection(start, end)
    }

    fun cancel() {
        // Invalidate rather than cancelling a possibly blocking InputConnection read. Its late
        // result cannot mutate this editor or a new session, even if the connection is reused.
        batch = null
    }

    fun onSelectionChanged(start: Int, end: Int) {
        val updated = Selection(start, end)
        if (selection != updated) cancel()
        selection = updated
    }

    fun enqueue() {
        val connection = currentConnection() ?: return
        if (!canDelete()) return
        batch?.takeIf(::isCurrent)?.let {
            it.pending++
            return
        }
        val request = Batch(connection, currentRevision())
        batch = request
        scope.launch {
            try {
                while (isCurrent(request) && request.pending > 0) {
                    val expectedSelection = selection
                    val before = readSnapshot(connection)
                    if (!isCurrent(request) ||
                        (before != null && expectedSelection.valid && before.selection != expectedSelection)
                    ) break

                    // A forward delete leaves the caret at the beginning of the removed range.
                    // Accept that callback as our own edit; any other selection change cancels.
                    val targetSelection = before?.selection ?: expectedSelection
                    selection = targetSelection.collapsed()
                    delete(targetSelection.selected)
                    request.revision = currentRevision()

                    // sendKeyEvent may post to the editor's view queue, so even a subsequent
                    // InputConnection read can precede the edit. Wait for the text acknowledgement
                    // before recording history or consuming the next queued tap.
                    var after = readSnapshot(connection)
                    var polls = 0
                    while (before != null && after == before && isCurrent(request) && polls < 32) {
                        delay(16)
                        if (!isCurrent(request)) break
                        after = readSnapshot(connection)
                        polls++
                    }
                    if (!isCurrent(request) || (after != null && after.selection != selection)) break
                    // If the editor ignores the key or does not acknowledge it in time, stop
                    // this batch rather than send more keys against an unconfirmed document.
                    if (before != null && after == before) break
                    if (before != null && after != null) {
                        val removed = removedText(before, after) ?: break
                        if (removed.isNotEmpty()) recordDeletion(removed)
                    }
                    // Editors may decline extracted-text requests (e.g. custom terminal views).
                    // Still deliver Delete, but never invent an undo entry without an acknowledgement.
                    request.pending--
                }
            } finally {
                if (batch === request) batch = null
            }
        }
    }

    private fun isCurrent(request: Batch): Boolean =
        batch === request && currentConnection() === request.connection &&
            currentRevision() == request.revision && canDelete()

    private suspend fun readSnapshot(connection: InputConnection): Snapshot? =
        withContext(readDispatcher) {
            val extracted = runCatching {
                connection.getExtractedText(ExtractedTextRequest(), 0)
            }.getOrNull() ?: return@withContext null
            val text = extracted.text?.toString() ?: return@withContext null
            if (extracted.partialStartOffset >= 0 || extracted.selectionStart !in 0..text.length ||
                extracted.selectionEnd !in 0..text.length
            ) return@withContext null
            Snapshot(
                text,
                extracted.startOffset,
                Selection(
                    extracted.startOffset + extracted.selectionStart,
                    extracted.startOffset + extracted.selectionEnd,
                ),
            )
        }

    private fun removedText(before: Snapshot, after: Snapshot): String? {
        if (before.offset != after.offset) return null
        val start = minOf(before.selection.start, before.selection.end) - before.offset
        val count = before.text.length - after.text.length
        if (count < 0 || start + count > before.text.length) return null
        if (before.text.removeRange(start, start + count) != after.text) return null
        return before.text.substring(start, start + count)
    }
}
