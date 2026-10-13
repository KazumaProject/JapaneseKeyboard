package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads the editor cursor off-main and applies a single horizontal cursor action on-main. */
internal class HorizontalCursorMoveHandler(
    private val scope: CoroutineScope,
    private val currentConnection: () -> InputConnection?,
    private val currentRevision: () -> Long,
    private val currentSelectionRevision: () -> Long,
    private val canCollapseSelection: () -> Boolean = { true },
    private val readMutex: Mutex,
    private val setSelection: (InputConnection, Int, Int) -> Boolean,
    private val sendDpad: (Direction) -> Unit,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    internal enum class Direction { Left, Right }

    private sealed class Decision {
        data class CollapseAt(val position: Int) : Decision()
        object CollapseWithDpad : Decision()
        object SendDpad : Decision()
        object NoOp : Decision()
    }

    private data class ReadResult(
        val decision: Decision,
        val revision: Long,
        val selectionRevision: Long,
    )

    fun move(direction: Direction) {
        val connection = currentConnection() ?: return
        scope.launch {
            val result = readMutex.withLock {
                val revision = currentRevision()
                val selectionRevision = currentSelectionRevision()
                val decision = withContext(readDispatcher) { resolveMove(connection, direction) }
                ReadResult(decision, revision, selectionRevision)
            }
            if (currentConnection() !== connection) return@launch

            withContext(mainDispatcher) {
                if (currentConnection() !== connection) return@withContext
                if ((result.decision is Decision.CollapseAt ||
                        result.decision == Decision.CollapseWithDpad) &&
                    (currentRevision() != result.revision ||
                        currentSelectionRevision() != result.selectionRevision ||
                        !canCollapseSelection())
                ) return@withContext
                when (val decision = result.decision) {
                    is Decision.CollapseAt -> {
                        val accepted = runCatching {
                            setSelection(connection, decision.position, decision.position)
                        }.getOrDefault(false)
                        if (!accepted) sendDpad(direction)
                    }

                    Decision.CollapseWithDpad -> sendDpad(direction)
                    Decision.SendDpad -> sendDpad(direction)
                    Decision.NoOp -> Unit
                }
            }
        }
    }

    private fun resolveMove(connection: InputConnection, direction: Direction): Decision {
        val extracted = runCatching {
            connection.getExtractedText(ExtractedTextRequest(), 0)
        }.getOrNull()
        val textLength = extracted?.text?.length
        if (extracted != null && textLength != null && extracted.partialStartOffset < 0 &&
            extracted.selectionStart in 0..textLength &&
            extracted.selectionEnd in 0..textLength
        ) {
            val start = extracted.selectionStart
            val end = extracted.selectionEnd
            if (start != end && extracted.startOffset >= 0) {
                val localTarget = if (direction == Direction.Left) minOf(start, end) else maxOf(start, end)
                return Decision.CollapseAt(extracted.startOffset + localTarget)
            }
            if (start == end) {
                val atBoundary = if (direction == Direction.Left) start == 0 else end == textLength
                return if (atBoundary) Decision.NoOp else Decision.SendDpad
            }
        }

        // Editors that decline extracted text can still report a selected range. Let the editor's
        // own DPAD handling collapse it because this fallback has no reliable absolute offset.
        val selectedText = runCatching { connection.getSelectedText(0) }.getOrNull()
        if (!selectedText.isNullOrEmpty()) return Decision.CollapseWithDpad

        val adjacentText = runCatching {
            if (direction == Direction.Left) {
                connection.getTextBeforeCursor(1, 0)
            } else {
                connection.getTextAfterCursor(1, 0)
            }
        }.getOrNull()
        return if (adjacentText.isNullOrEmpty()) Decision.NoOp else Decision.SendDpad
    }
}
