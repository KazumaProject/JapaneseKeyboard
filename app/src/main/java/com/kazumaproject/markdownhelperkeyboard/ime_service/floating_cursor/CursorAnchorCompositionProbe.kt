package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.util.Log
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the editor's composing range independently of styles stripped from text snapshots.
 * [read], [onUpdate] and [reset] belong to the caller's main dispatcher.
 */
internal class CursorAnchorCompositionProbe(
    private val connection: () -> InputConnection?,
    private val revision: () -> Long,
    private val cursorUpdateMode: () -> Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = 300L,
) {
    private class Pending(
        val id: Long,
        val editor: InputConnection,
        val start: Int,
        val end: Int,
        val revision: Long,
    ) {
        val result = CompletableDeferred<Boolean?>()
        var request: Job? = null
        var acceptingUpdates = false
        var receivedEarlyUpdate = false
    }

    // A remote request may itself wait for seconds. It must not hold the input queue
    // past our timeout merely because a blocking IPC cannot immediately be cancelled.
    private val requests = CoroutineScope(SupervisorJob() + dispatcher)
    private var pending: Pending? = null
    private var nextId = 0L

    suspend fun read(editor: InputConnection, start: Int, end: Int): Boolean? {
        reset()
        if (connection() !== editor || start < 0 || end < 0) return null
        val request = Pending(++nextId, editor, start, end, revision())
        pending = request
        val ownerContext = currentCoroutineContext().minusKey(Job)
        // Request all anchor fields, retaining any existing physical-keyboard monitoring.
        val mode = InputConnection.CURSOR_UPDATE_IMMEDIATE or
            (cursorUpdateMode() and InputConnection.CURSOR_UPDATE_MONITOR)
        trace("request id=${request.id} version=${request.revision} selection=$start,$end mode=$mode")
        request.request = requests.launch {
            val accepted = runCatching { editor.requestCursorUpdates(mode) }.getOrDefault(false)
            var retry = false
            withContext(ownerContext) {
                trace("accepted id=${request.id} accepted=$accepted current=${isCurrent(request)} early=${request.receivedEarlyUpdate}")
                if (isCurrent(request)) {
                    if (!accepted) request.result.complete(null)
                    else {
                        request.acceptingUpdates = true
                        retry = request.receivedEarlyUpdate
                    }
                }
            }
            // A fast editor can notify before its request response reaches this dispatcher.
            // Ignore that update and ask for another, rather than treating a queued monitor
            // update observed before acceptance as the answer to this request.
            if (retry) {
                trace("retry id=${request.id}")
                val retried = runCatching { editor.requestCursorUpdates(mode) }.getOrDefault(false)
                if (!retried) withContext(ownerContext) {
                    if (isCurrent(request)) request.result.complete(null)
                }
            }
        }
        return try {
            withTimeoutOrNull(timeoutMillis) { request.result.await() }.also {
                trace("result id=${request.id} nativeComposing=$it current=${isCurrent(request)}")
            }
        } finally {
            trace("finish id=${request.id}")
            if (pending === request) pending = null
            request.request?.cancel()
        }
    }

    fun onUpdate(info: CursorAnchorInfo?) {
        val request = pending ?: return
        trace("update id=${request.id} version=${revision()} current=${isCurrent(request)} accepting=${request.acceptingUpdates} selection=${info?.selectionStart},${info?.selectionEnd} composingStart=${info?.composingTextStart} composingLength=${info?.composingText?.length}")
        if (info == null || !isCurrent(request) || info.selectionStart != request.start ||
            info.selectionEnd != request.end) return
        if (!request.acceptingUpdates) {
            request.receivedEarlyUpdate = true
            return
        }
        // CursorAnchorInfo transports the range/text as fields. Unlike SPAN_COMPOSING,
        // these survive parceling even when the editor uses a non-Parcelable composing span.
        val text = info.composingText
        when {
            info.composingTextStart >= 0 && !text.isNullOrEmpty() -> request.result.complete(true)
            info.composingTextStart < 0 && text.isNullOrEmpty() -> request.result.complete(false)
        }
    }

    fun reset() {
        val request = pending
        if (request != null) trace("reset id=${request.id}")
        pending = null
        request?.result?.complete(null)
        request?.request?.cancel()
    }

    private fun isCurrent(request: Pending): Boolean = pending === request &&
        connection() === request.editor && revision() == request.revision

    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d("CursorAnchorProbe", message)
    }
}
