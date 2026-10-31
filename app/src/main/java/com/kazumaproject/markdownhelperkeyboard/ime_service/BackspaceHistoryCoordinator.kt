package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Serializes editor backspaces and records only text the editor actually removed. */
internal class BackspaceHistoryCoordinator(
    private val scope: CoroutineScope,
    private val connection: () -> InputConnection?,
    private val revision: () -> Long,
    private val session: () -> Long,
    private val canDelete: () -> Boolean,
    private val delete: () -> Unit,
    private val record: (String, Long?) -> Unit,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val selectionRevision: () -> Long = { 0L },
    private val latestSelection: () -> Pair<Int, Int>? = { null },
    private val invalidateHistory: () -> Unit = {},
) {
    private data class Snapshot(val text: String, val offset: Int, val start: Int, val end: Int)
    private data class Request(val editor: InputConnection, val session: Long, val group: Long?)
    private val pending = ArrayDeque<Request>()
    private var worker: Job? = null
    private var generation = 0L
    private val whenIdle = mutableListOf<() -> Unit>()
    private var inFlight: Request? = null
    private var deleteIssued = false

    fun cancel() {
        generation++
        pending.clear()
        whenIdle.clear()
        worker?.cancel()
        worker = null
        inFlight = null
        deleteIssued = false
    }

    /** Preserve already requested deletions before a later key changes the editor. */
    fun flushPendingAndCancel() {
        val token = generation
        var needsInvalidation = false
        inFlight?.takeIf { current(it, token) }?.let {
            if (!deleteIssued) {
                deleteIssued = true
                delete()
            }
            needsInvalidation = true
        }
        while (pending.isNotEmpty()) {
            val request = pending.removeFirst()
            if (!current(request, token)) break
            delete()
            needsInvalidation = true
        }
        if (needsInvalidation) invalidateHistory()
        cancel()
    }

    fun afterPending(action: () -> Unit) {
        if (worker?.isActive == true || pending.isNotEmpty()) whenIdle.add(action) else action()
    }

    fun enqueue(group: Long? = null) {
        val editor = connection() ?: return
        if (!canDelete()) return
        pending.addLast(Request(editor, session(), group))
        if (worker?.isActive == true) return
        val token = generation
        worker = scope.launch {
            var confirmed = true
            try {
                while (pending.isNotEmpty()) {
                    val request = pending.removeFirst()
                    inFlight = request
                    deleteIssued = false
                    val beforeRevision = revision()
                    val beforeSelectionRevision = selectionRevision()
                    val before = snapshot(request.editor)
                    if (!current(request, token) || revision() != beforeRevision ||
                        (selectionRevision() != beforeSelectionRevision && !matchesSelection(before))
                    ) {
                        confirmed = false
                        break
                    }
                    deleteIssued = true
                    delete()
                    val deletionRevision = revision()
                    if (!current(request, token)) { confirmed = false; break }
                    var after = if (before != null) snapshot(request.editor) else null
                    var attempts = 0
                    while (before != null && after == before && current(request, token) &&
                        revision() == deletionRevision && attempts++ < 32
                    ) {
                        delay(16)
                        after = snapshot(request.editor)
                    }
                    if (!current(request, token) || revision() != deletionRevision) {
                        if (current(request, token)) invalidateHistory()
                        confirmed = false
                        break
                    }
                    if (before == null || after == null || after == before) {
                        // The editor did not provide a reliable acknowledgement. Deliver the
                        // remaining taps, but do not retain history that could restore stale text.
                        invalidateHistory()
                        while (pending.isNotEmpty()) {
                            val next = pending.removeFirst()
                            if (!current(next, token)) { confirmed = false; break }
                            delete()
                        }
                        break
                    }
                    val removed = removedText(before, after)
                    if (removed == null) {
                        invalidateHistory()
                        while (pending.isNotEmpty()) {
                            val next = pending.removeFirst()
                            if (!current(next, token)) { confirmed = false; break }
                            delete()
                        }
                        break
                    }
                    if (removed.isNotEmpty()) record(removed, request.group)
                    inFlight = null
                    deleteIssued = false
                }
            } finally {
                if (token == generation) {
                    pending.clear()
                    worker = null
                    inFlight = null
                    deleteIssued = false
                    val callbacks = whenIdle.toList()
                    whenIdle.clear()
                    if (confirmed) callbacks.forEach { it() }
                }
            }
        }
    }

    private fun current(request: Request, token: Long): Boolean =
        generation == token && connection() === request.editor && session() == request.session && canDelete()

    private fun matchesSelection(snapshot: Snapshot?): Boolean {
        val latest = latestSelection() ?: return false
        snapshot ?: return false
        val first = snapshot.start + snapshot.offset
        val second = snapshot.end + snapshot.offset
        return minOf(first, second) == minOf(latest.first, latest.second) &&
            maxOf(first, second) == maxOf(latest.first, latest.second)
    }

    private suspend fun snapshot(editor: InputConnection): Snapshot? = withContext(readDispatcher) {
        val extracted = runCatching { editor.getExtractedText(ExtractedTextRequest(), 0) }.getOrNull()
            ?: return@withContext null
        val text = extracted.text?.toString() ?: return@withContext null
        if (extracted.startOffset < 0 || extracted.partialStartOffset >= 0 ||
            extracted.selectionStart !in 0..text.length || extracted.selectionEnd !in 0..text.length
        ) return@withContext null
        Snapshot(text, extracted.startOffset, extracted.selectionStart, extracted.selectionEnd)
    }

    private fun removedText(before: Snapshot, after: Snapshot): String? {
        if (before.offset != after.offset || after.start != after.end) return null
        val oldStart = minOf(before.start, before.end)
        val oldEnd = maxOf(before.start, before.end)
        val start = after.start
        val end = if (oldStart == oldEnd) oldStart else oldEnd
        if (start < 0 || start > end || end > before.text.length ||
            (oldStart != oldEnd && start != oldStart)
        ) return null
        if (before.text.removeRange(start, end) != after.text) return null
        return before.text.substring(start, end)
    }
}
